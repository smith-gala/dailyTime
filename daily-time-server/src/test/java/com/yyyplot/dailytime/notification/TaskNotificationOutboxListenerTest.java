package com.yyyplot.dailytime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.entity.TaskNotificationTargetEntity;
import com.yyyplot.dailytime.entity.NotificationOutboxEntity;
import com.yyyplot.dailytime.enums.NotificationOutboxStatus;
import com.yyyplot.dailytime.enums.NotificationType;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.mapper.AutomationTaskMapper;
import com.yyyplot.dailytime.mapper.NotificationOutboxMapper;
import com.yyyplot.dailytime.service.ConversationTaskBindingService;
import com.yyyplot.dailytime.workflow.TaskStatusChangedEvent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

class TaskNotificationOutboxListenerTest {
    @Test
    void createsIdempotentReviewNotificationWithMedia() {
        ConversationTaskBindingService bindingService =
                mock(ConversationTaskBindingService.class);
        AutomationTaskMapper taskMapper = mock(AutomationTaskMapper.class);
        NotificationOutboxMapper outboxMapper = mock(NotificationOutboxMapper.class);
        AutomationTaskEntity task = new AutomationTaskEntity();
        task.setTaskId("20260904_a1b2c3d4");
        task.setSentence("Keep going");
        task.setStatus(TaskStatus.AWAITING_REVIEW);
        task.setVideoFile("D:/video.mp4");
        TaskNotificationTargetEntity target = new TaskNotificationTargetEntity();
        target.setConversationId("oc_chat");
        target.setUserId("ou_user");
        when(taskMapper.findByTaskId(task.getTaskId())).thenReturn(task);
        when(bindingService.findNotificationTargets(task.getTaskId())).thenReturn(List.of(target));
        TaskNotificationOutboxListener listener =
                new TaskNotificationOutboxListener(
                        bindingService, taskMapper, outboxMapper, new ObjectMapper());
        TaskStatusChangedEvent event =
                new TaskStatusChangedEvent(
                        task.getTaskId(),
                        TaskStatus.RENDERING,
                        TaskStatus.AWAITING_REVIEW,
                        LocalDateTime.of(2026, 9, 4, 20, 0));

        listener.enqueue(event);
        listener.enqueue(event);

        ArgumentCaptor<NotificationOutboxEntity> captor =
                ArgumentCaptor.forClass(NotificationOutboxEntity.class);
        verify(outboxMapper, times(2)).insert(captor.capture());
        NotificationOutboxEntity outbox = captor.getAllValues().get(0);
        assertThat(outbox.getNotificationType()).isEqualTo(NotificationType.AWAITING_REVIEW);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outbox.getPayloadJson())
                .contains("D:/video.mp4", "通过 / 取消")
                .doesNotContain("重新生成");
        assertThat(outbox.getEventId()).hasSize(36);
        assertThat(captor.getAllValues().get(1).getEventId()).isEqualTo(outbox.getEventId());
    }
}
