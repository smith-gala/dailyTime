package com.yyyplot.dailytime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.dto.task.CreateTaskCommand;
import com.yyyplot.dailytime.dto.task.TaskProgressCommand;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.entity.SentenceEntity;
import com.yyyplot.dailytime.enums.SentenceStatus;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.mapper.AutomationTaskMapper;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.impl.TaskServiceImpl;
import com.yyyplot.dailytime.vo.CreateTaskResult;
import com.yyyplot.dailytime.workflow.TaskStateMachine;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

class TaskServiceImplTest {
    @Test
    void duplicateActiveTaskIsIdempotent() {
        AutomationTaskMapper taskMapper = mock(AutomationTaskMapper.class);
        SentenceService sentenceService = mock(SentenceService.class);
        SentenceEntity sentence = sentence();
        AutomationTaskEntity activeTask = task(TaskStatus.DOWNLOADING, 2);
        when(sentenceService.findOrCreate(anyString(), any())).thenReturn(sentence);
        when(taskMapper.findByActiveKey("ive had it")).thenReturn(activeTask);
        TaskServiceImpl service = service(taskMapper, sentenceService);

        CreateTaskResult result =
                service.createTask(
                        new CreateTaskCommand("I've had it", "受够了", false, false),
                        UserContext.apiUser("u1"));

        assertThat(result.created()).isFalse();
        assertThat(result.taskId()).isEqualTo(activeTask.getTaskId());
        verify(taskMapper, never()).insert(any(AutomationTaskEntity.class));
    }

    @Test
    void databaseUniqueConstraintWinsCreationRace() {
        AutomationTaskMapper taskMapper = mock(AutomationTaskMapper.class);
        SentenceService sentenceService = mock(SentenceService.class);
        SentenceEntity sentence = sentence();
        AutomationTaskEntity activeTask = task(TaskStatus.QUEUED, 0);
        when(sentenceService.findOrCreate(anyString(), any())).thenReturn(sentence);
        when(taskMapper.findByActiveKey("ive had it")).thenReturn(null, activeTask);
        when(taskMapper.insert(any(AutomationTaskEntity.class)))
                .thenThrow(new DuplicateKeyException("unique active_key"));

        CreateTaskResult result =
                service(taskMapper, sentenceService)
                        .createTask(
                                new CreateTaskCommand("I've had it", "受够了", false, false),
                                UserContext.apiUser("u1"));

        assertThat(result.created()).isFalse();
        assertThat(result.taskId()).isEqualTo(activeTask.getTaskId());
    }

    @Test
    void onlySuccessfulConditionalClaimCanRun() {
        AutomationTaskMapper taskMapper = mock(AutomationTaskMapper.class);
        AutomationTaskEntity queued = task(TaskStatus.QUEUED, 7);
        when(taskMapper.findByTaskId(queued.getTaskId())).thenReturn(queued);
        when(taskMapper.claimQueuedTask(queued.getTaskId(), 7)).thenReturn(1, 0);
        TaskServiceImpl service = service(taskMapper, mock(SentenceService.class));

        assertThat(service.claimTask(queued.getTaskId())).isTrue();
        assertThat(service.claimTask(queued.getTaskId())).isFalse();
        verify(taskMapper, org.mockito.Mockito.times(2))
                .claimQueuedTask(queued.getTaskId(), 7);
    }

    @Test
    void progressUpdateReportsOptimisticConflict() {
        AutomationTaskMapper taskMapper = mock(AutomationTaskMapper.class);
        AutomationTaskEntity downloading = task(TaskStatus.DOWNLOADING, 3);
        when(taskMapper.findByTaskId(downloading.getTaskId())).thenReturn(downloading);
        when(taskMapper.updateProgress(downloading.getTaskId(), "DOWNLOADING", 55, "下载中", 3))
                .thenReturn(0);

        assertThatThrownBy(
                        () ->
                                service(taskMapper, mock(SentenceService.class))
                                        .updateProgress(
                                                new TaskProgressCommand(
                                                        downloading.getTaskId(),
                                                        TaskStatus.DOWNLOADING,
                                                        55,
                                                        "下载中")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void stateTransitionReportsMybatisOptimisticLockConflict() {
        AutomationTaskMapper taskMapper = mock(AutomationTaskMapper.class);
        AutomationTaskEntity analyzing = task(TaskStatus.ANALYZING, 3);
        when(taskMapper.findByTaskId(analyzing.getTaskId())).thenReturn(analyzing);
        when(taskMapper.updateById(analyzing)).thenReturn(0);

        assertThatThrownBy(
                        () ->
                                service(taskMapper, mock(SentenceService.class))
                                        .markAnalyzed(analyzing.getTaskId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void runningTaskCannotBeCanceled() {
        AutomationTaskMapper taskMapper = mock(AutomationTaskMapper.class);
        AutomationTaskEntity rendering = task(TaskStatus.RENDERING, 3);
        when(taskMapper.findByTaskId(rendering.getTaskId())).thenReturn(rendering);

        assertThatThrownBy(
                        () ->
                                service(taskMapper, mock(SentenceService.class))
                                        .cancelTask(
                                                rendering.getTaskId(),
                                                UserContext.apiUser("u1")))
                .isInstanceOf(BusinessException.class);
        verify(taskMapper, never()).updateById(any(AutomationTaskEntity.class));
    }

    private TaskServiceImpl service(
            AutomationTaskMapper taskMapper,
            SentenceService sentenceService) {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        return new TaskServiceImpl(
                taskMapper,
                sentenceService,
                mock(AdoptionService.class),
                new TaskStateMachine(publisher),
                publisher);
    }

    private SentenceEntity sentence() {
        SentenceEntity sentence = new SentenceEntity();
        sentence.setId(1L);
        sentence.setEnglish("I've had it");
        sentence.setNormalizedEnglish("ive had it");
        sentence.setStatus(SentenceStatus.PENDING);
        return sentence;
    }

    private AutomationTaskEntity task(TaskStatus status, int version) {
        AutomationTaskEntity task = new AutomationTaskEntity();
        task.setTaskId("20260902_a1b2c3d4");
        task.setSentence("I've had it");
        task.setStatus(status);
        task.setProgressPercent(40);
        task.setProgressLabel("处理中");
        task.setCreatedBy("u1");
        task.setVersion(version);
        return task;
    }
}
