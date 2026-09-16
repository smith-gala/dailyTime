package com.yyyplot.dailytime.service.impl;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.dto.task.CreateTaskCommand;
import com.yyyplot.dailytime.dto.task.TaskProgressCommand;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.entity.SentenceEntity;
import com.yyyplot.dailytime.enums.SentenceStatus;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.enums.WorkerErrorCode;
import com.yyyplot.dailytime.mapper.AutomationTaskMapper;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.AdoptionService;
import com.yyyplot.dailytime.service.SentenceService;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.vo.CreateTaskResult;
import com.yyyplot.dailytime.vo.TaskDetailVO;
import com.yyyplot.dailytime.worker.WorkerExecutionException;
import com.yyyplot.dailytime.workflow.TaskQueuedEvent;
import com.yyyplot.dailytime.workflow.TaskStateMachine;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskServiceImpl implements TaskService {
    private static final int MAXIMUM_ERROR_MESSAGE_LENGTH = 1000;
    private static final DateTimeFormatter TASK_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<TaskStatus> TERMINAL_STATUSES =
            Set.of(TaskStatus.ADOPTED, TaskStatus.CANCELED, TaskStatus.FAILED);

    private final AutomationTaskMapper taskMapper;
    private final SentenceService sentenceService;
    private final AdoptionService adoptionService;
    private final TaskStateMachine taskStateMachine;
    private final ApplicationEventPublisher eventPublisher;

    public TaskServiceImpl(
            AutomationTaskMapper taskMapper,
            SentenceService sentenceService,
            AdoptionService adoptionService,
            TaskStateMachine taskStateMachine,
            ApplicationEventPublisher eventPublisher) {
        this.taskMapper = taskMapper;
        this.sentenceService = sentenceService;
        this.adoptionService = adoptionService;
        this.taskStateMachine = taskStateMachine;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public CreateTaskResult createTask(CreateTaskCommand command, UserContext user) {
        SentenceEntity sentence = resolveSentence(command);
        if (sentence.getStatus() == SentenceStatus.ADOPTED && !command.confirmedAdopted()) {
            throw new BusinessException(
                    ErrorCode.TOOL_CONFIRMATION_REQUIRED, "该句子已采纳，重新制作需要明确确认");
        }

        AutomationTaskEntity activeTask = taskMapper.findByActiveKey(sentence.getNormalizedEnglish());
        if (activeTask != null) {
            return existingTask(activeTask, user);
        }

        AutomationTaskEntity task = queuedTask(sentence, command, user);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException duplicateKeyException) {
            activeTask = taskMapper.findByActiveKey(sentence.getNormalizedEnglish());
            if (activeTask == null) {
                throw duplicateKeyException;
            }
            return existingTask(activeTask, user);
        }

        // 监听器仅在创建事务提交后把任务交给有界线程池。
        eventPublisher.publishEvent(new TaskQueuedEvent(task.getTaskId()));
        return new CreateTaskResult(task.getTaskId(), true, TaskStatus.QUEUED.name());
    }

    @Override
    public TaskDetailVO getTask(String taskId, UserContext user) {
        return toTaskDetail(requireOwned(taskId, user));
    }

    @Override
    @Transactional
    public TaskDetailVO approveTask(String taskId, UserContext user) {
        AutomationTaskEntity task = requireOwned(taskId, user);
        TaskStatus previousStatus = task.getStatus();
        adoptionService.adopt(task, user);
        AutomationTaskEntity adoptedTask = requireEntity(taskId);
        taskStateMachine.publishTransition(taskId, previousStatus, adoptedTask.getStatus());
        return toTaskDetail(adoptedTask);
    }

    @Override
    @Transactional
    public TaskDetailVO cancelTask(String taskId, UserContext user) {
        AutomationTaskEntity task = requireOwned(taskId, user);
        TaskStatus previousStatus = task.getStatus();
        taskStateMachine.requireTransition(previousStatus, TaskStatus.CANCELED);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TaskStatus.CANCELED);
        task.setProgressLabel("任务已取消");
        task.setActiveKey(null);
        task.setCompletedAt(now);
        task.setUpdatedAt(now);
        updateOrConflict(task);
        taskStateMachine.publishTransition(taskId, previousStatus, TaskStatus.CANCELED);
        return toTaskDetail(task);
    }

    @Override
    @Transactional
    public boolean claimTask(String taskId) {
        AutomationTaskEntity task = requireEntity(taskId);
        int claimedRows = taskMapper.claimQueuedTask(taskId, task.getVersion());
        if (claimedRows == 1) {
            taskStateMachine.publishTransition(taskId, TaskStatus.QUEUED, TaskStatus.ANALYZING);
        }
        return claimedRows == 1;
    }

    @Override
    @Transactional
    public void updateProgress(TaskProgressCommand command) {
        AutomationTaskEntity task = requireEntity(command.taskId());
        int updatedRows =
                taskMapper.updateProgress(
                        command.taskId(),
                        command.status().name(),
                        command.progress(),
                        command.label(),
                        task.getVersion());
        if (updatedRows != 1) {
            throw conflict("任务进度已被其他请求修改");
        }
    }

    @Override
    @Transactional
    public void markAnalyzed(String taskId) {
        transition(taskId, TaskStatus.DOWNLOADING, 40, "正在下载素材", null);
    }

    @Override
    @Transactional
    public void markDownloaded(String taskId) {
        transition(taskId, TaskStatus.RENDERING, 70, "正在渲染视频", null);
    }

    @Override
    @Transactional
    public void markAwaitingReview(String taskId, Path videoFile) {
        if (videoFile == null) {
            throw new BusinessException(ErrorCode.INVALID_WORKER_RESPONSE, "渲染结果缺少成片路径");
        }
        transition(taskId, TaskStatus.AWAITING_REVIEW, 95, "成片生成，等待审核", videoFile.toString());
    }

    @Override
    @Transactional
    public void markFailed(String taskId, Throwable error) {
        AutomationTaskEntity task = requireEntity(taskId);
        TaskStatus previousStatus = task.getStatus();
        if (TERMINAL_STATUSES.contains(previousStatus)) {
            return;
        }
        taskStateMachine.requireTransition(previousStatus, TaskStatus.FAILED);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TaskStatus.FAILED);
        task.setActiveKey(null);
        task.setErrorCode(resolveErrorCode(error));
        task.setErrorMessage(sanitizeErrorMessage(error.getMessage()));
        task.setProgressLabel("任务执行失败");
        task.setCompletedAt(now);
        task.setUpdatedAt(now);
        updateOrConflict(task);
        taskStateMachine.publishTransition(taskId, previousStatus, TaskStatus.FAILED);
    }

    @Override
    public AutomationTaskEntity requireEntity(String taskId) {
        AutomationTaskEntity task = taskMapper.findByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在");
        }
        return task;
    }

    @Override
    public List<AutomationTaskEntity> findRunning() {
        return taskMapper.findRunningTasks();
    }

    private SentenceEntity resolveSentence(CreateTaskCommand command) {
        return command.useNext()
                ? sentenceService.nextAvailable()
                : sentenceService.findOrCreate(command.sentence(), command.translation());
    }

    private AutomationTaskEntity queuedTask(
            SentenceEntity sentence,
            CreateTaskCommand command,
            UserContext user) {
        LocalDateTime now = LocalDateTime.now();
        AutomationTaskEntity task = new AutomationTaskEntity();
        task.setTaskId(newTaskId());
        task.setSentenceId(sentence.getId());
        task.setSentence(sentence.getEnglish());
        task.setNormalizedSentence(sentence.getNormalizedEnglish());
        task.setActiveKey(sentence.getNormalizedEnglish());
        task.setRequestedTranslation(cleanText(command.translation()));
        task.setStatus(TaskStatus.QUEUED);
        task.setProgressPercent(0);
        task.setProgressLabel("等待执行");
        task.setCreatedBy(user.userId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setVersion(0);
        return task;
    }

    private CreateTaskResult existingTask(AutomationTaskEntity task, UserContext user) {
        boolean ownsTask = Objects.equals(task.getCreatedBy(), user.userId());
        if (!user.administrator() && !ownsTask) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING, "该句子已有任务正在执行");
        }
        return new CreateTaskResult(task.getTaskId(), false, task.getStatus().name());
    }

    private void transition(
            String taskId,
            TaskStatus nextStatus,
            int progress,
            String label,
            String videoFile) {
        AutomationTaskEntity task = requireEntity(taskId);
        TaskStatus previousStatus = task.getStatus();
        taskStateMachine.requireTransition(previousStatus, nextStatus);
        task.setStatus(nextStatus);
        task.setProgressPercent(progress);
        task.setProgressLabel(label);
        if (videoFile != null) {
            task.setVideoFile(videoFile);
        }
        task.setUpdatedAt(LocalDateTime.now());
        updateOrConflict(task);
        taskStateMachine.publishTransition(taskId, previousStatus, nextStatus);
    }

    private AutomationTaskEntity requireOwned(String taskId, UserContext user) {
        AutomationTaskEntity task = requireEntity(taskId);
        boolean ownsTask = Objects.equals(task.getCreatedBy(), user.userId());
        if (!user.administrator() && !ownsTask) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该任务");
        }
        return task;
    }

    private TaskDetailVO toTaskDetail(AutomationTaskEntity task) {
        return new TaskDetailVO(
                task.getTaskId(),
                task.getSentence(),
                task.getStatus(),
                task.getProgressPercent(),
                task.getProgressLabel(),
                task.getErrorCode(),
                task.getErrorMessage(),
                task.getVideoFile());
    }

    private String resolveErrorCode(Throwable error) {
        if (error instanceof WorkerExecutionException workerException) {
            return workerException.code().name();
        }
        if (error instanceof BusinessException businessException) {
            return businessException.code().name();
        }
        return WorkerErrorCode.INTERNAL_ERROR.name();
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "未知错误";
        }
        return errorMessage.length() > MAXIMUM_ERROR_MESSAGE_LENGTH
                ? errorMessage.substring(0, MAXIMUM_ERROR_MESSAGE_LENGTH)
                : errorMessage;
    }

    private void updateOrConflict(AutomationTaskEntity task) {
        if (taskMapper.updateById(task) != 1) {
            throw conflict("任务状态已被其他请求修改");
        }
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.TASK_STATE_CONFLICT, message);
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private String newTaskId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return LocalDateTime.now().format(TASK_DATE_FORMATTER) + "_" + suffix;
    }
}
