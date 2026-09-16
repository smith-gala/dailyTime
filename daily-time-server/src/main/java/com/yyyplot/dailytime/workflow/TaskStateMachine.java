package com.yyyplot.dailytime.workflow;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.enums.TaskStatus;

import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Component
public class TaskStateMachine {
    private final ApplicationEventPublisher eventPublisher;

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS =
            Map.ofEntries(
                    Map.entry(
                            TaskStatus.QUEUED,
                            Set.of(TaskStatus.ANALYZING, TaskStatus.FAILED)),
                    Map.entry(
                            TaskStatus.ANALYZING,
                            Set.of(TaskStatus.DOWNLOADING, TaskStatus.FAILED)),
                    Map.entry(
                            TaskStatus.DOWNLOADING,
                            Set.of(TaskStatus.RENDERING, TaskStatus.FAILED)),
                    Map.entry(
                            TaskStatus.RENDERING,
                            Set.of(TaskStatus.AWAITING_REVIEW, TaskStatus.FAILED)),
                    Map.entry(
                            TaskStatus.AWAITING_REVIEW,
                            Set.of(TaskStatus.ADOPTED, TaskStatus.CANCELED)),
                    Map.entry(TaskStatus.ADOPTED, Set.of()),
                    Map.entry(TaskStatus.CANCELED, Set.of()),
                    Map.entry(TaskStatus.FAILED, Set.of()));

    public TaskStateMachine(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public boolean canTransition(TaskStatus current, TaskStatus next) {
        Set<TaskStatus> allowedStatuses = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        return allowedStatuses.contains(next);
    }

    public void requireTransition(TaskStatus current, TaskStatus next) {
        // 所有状态转换都经此处，避免不同 Service 对同一状态产生不同解释。
        if (!canTransition(current, next)) {
            throw new BusinessException(
                    ErrorCode.TASK_STATE_CONFLICT, "不允许从 " + current + " 转换到 " + next);
        }
    }

    public void publishTransition(
            String taskId,
            TaskStatus previousStatus,
            TaskStatus currentStatus) {
        if (previousStatus == currentStatus) {
            return;
        }
        eventPublisher.publishEvent(
                new TaskStatusChangedEvent(
                        taskId,
                        previousStatus,
                        currentStatus,
                        LocalDateTime.now()));
    }

    public void publishCurrentStatus(
            String taskId,
            TaskStatus currentStatus) {
        eventPublisher.publishEvent(
                new TaskStatusChangedEvent(
                        taskId,
                        null,
                        currentStatus,
                        LocalDateTime.now()));
    }
}
