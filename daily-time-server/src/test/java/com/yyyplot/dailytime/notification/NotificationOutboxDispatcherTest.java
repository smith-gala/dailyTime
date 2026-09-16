package com.yyyplot.dailytime.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

import com.yyyplot.dailytime.entity.NotificationOutboxEntity;
import com.yyyplot.dailytime.mapper.NotificationOutboxMapper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

class NotificationOutboxDispatcherTest {
    @Test
    void claimsBeforeSendingAndMarksSent() {
        NotificationOutboxMapper outboxMapper = mock(NotificationOutboxMapper.class);
        FeishuNotificationSender sender = mock(FeishuNotificationSender.class);
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setEventId("event-1");
        outbox.setTaskId("task-1");
        outbox.setAttemptCount(0);
        when(outboxMapper.findDue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(List.of(outbox));
        when(outboxMapper.claim(
                        org.mockito.ArgumentMatchers.eq("event-1"),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(1);
        NotificationOutboxDispatcher dispatcher =
                new NotificationOutboxDispatcher(
                        outboxMapper,
                        sender,
                        true,
                        6,
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5));

        dispatcher.dispatchDueNotifications();

        verify(sender).send(outbox);
        verify(outboxMapper)
                .markSent(
                        org.mockito.ArgumentMatchers.eq("event-1"),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void doesNotSendWhenConditionalClaimLoses() {
        NotificationOutboxMapper outboxMapper = mock(NotificationOutboxMapper.class);
        FeishuNotificationSender sender = mock(FeishuNotificationSender.class);
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setEventId("event-1");
        outbox.setAttemptCount(0);
        when(outboxMapper.findDue(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(List.of(outbox));
        when(outboxMapper.claim(
                        org.mockito.ArgumentMatchers.eq("event-1"),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(0);
        NotificationOutboxDispatcher dispatcher =
                new NotificationOutboxDispatcher(
                        outboxMapper,
                        sender,
                        true,
                        6,
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5));

        dispatcher.dispatchDueNotifications();

        verifyNoInteractions(sender);
    }

    @Test
    void usesExponentialBackoff() {
        NotificationOutboxDispatcher dispatcher =
                new NotificationOutboxDispatcher(
                        mock(NotificationOutboxMapper.class),
                        mock(FeishuNotificationSender.class),
                        true,
                        6,
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5));

        assertThat(dispatcher.retryDelayFor(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(dispatcher.retryDelayFor(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(dispatcher.retryDelayFor(3)).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void recoversStaleProcessingRowsBeforeScanning() {
        NotificationOutboxMapper outboxMapper = mock(NotificationOutboxMapper.class);
        NotificationOutboxDispatcher dispatcher =
                new NotificationOutboxDispatcher(
                        outboxMapper,
                        mock(FeishuNotificationSender.class),
                        true,
                        6,
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5));

        dispatcher.dispatchDueNotifications();

        verify(outboxMapper)
                .recoverStale(
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }
}
