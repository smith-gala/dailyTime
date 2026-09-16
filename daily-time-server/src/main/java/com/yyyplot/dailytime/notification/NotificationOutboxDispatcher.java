package com.yyyplot.dailytime.notification;

import com.yyyplot.dailytime.entity.NotificationOutboxEntity;
import com.yyyplot.dailytime.mapper.NotificationOutboxMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class NotificationOutboxDispatcher {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(NotificationOutboxDispatcher.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAXIMUM_ERROR_LENGTH = 1000;

    private final NotificationOutboxMapper outboxMapper;
    private final FeishuNotificationSender notificationSender;
    private final boolean enabled;
    private final int maximumAttempts;
    private final Duration retryBaseDelay;
    private final Duration processingStaleAfter;

    public NotificationOutboxDispatcher(
            NotificationOutboxMapper outboxMapper,
            FeishuNotificationSender notificationSender,
            @Value("${daily-time.notification.enabled:true}") boolean enabled,
            @Value("${daily-time.notification.max-attempts:6}") int maximumAttempts,
            @Value("${daily-time.notification.retry-base-delay:10s}") Duration retryBaseDelay,
            @Value("${daily-time.notification.processing-stale-after:5m}")
                    Duration processingStaleAfter) {
        this.outboxMapper = outboxMapper;
        this.notificationSender = notificationSender;
        this.enabled = enabled;
        this.maximumAttempts = maximumAttempts;
        this.retryBaseDelay = retryBaseDelay;
        this.processingStaleAfter = processingStaleAfter;
    }

    @Scheduled(fixedDelayString = "${daily-time.notification.poll-interval:5s}")
    public void dispatchDueNotifications() {
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int recovered = outboxMapper.recoverStale(now.minus(processingStaleAfter), now);
        if (recovered > 0) {
            LOGGER.warn("已恢复 {} 条发送中断的飞书通知", recovered);
        }

        List<NotificationOutboxEntity> notifications = outboxMapper.findDue(now, BATCH_SIZE);
        for (NotificationOutboxEntity notification : notifications) {
            dispatchOne(notification);
        }
    }

    private void dispatchOne(NotificationOutboxEntity notification) {
        LocalDateTime claimedAt = LocalDateTime.now();
        if (outboxMapper.claim(notification.getEventId(), claimedAt) != 1) {
            return;
        }

        int attempt = notification.getAttemptCount() + 1;
        try {
            notificationSender.send(notification);
            outboxMapper.markSent(notification.getEventId(), LocalDateTime.now());
        } catch (RuntimeException sendError) {
            String errorMessage = sanitizeError(sendError);
            if (attempt >= maximumAttempts) {
                outboxMapper.markDead(notification.getEventId(), errorMessage, LocalDateTime.now());
                LOGGER.error(
                        "飞书通知重试耗尽，eventId={} taskId={} error={}",
                        notification.getEventId(),
                        notification.getTaskId(),
                        errorMessage);
                return;
            }
            Duration retryDelay = retryDelayFor(attempt);
            LocalDateTime now = LocalDateTime.now();
            outboxMapper.markRetry(
                    notification.getEventId(), now.plus(retryDelay), errorMessage, now);
            LOGGER.warn(
                    "飞书通知发送失败，等待重试，eventId={} attempt={} error={}",
                    notification.getEventId(),
                    attempt,
                    errorMessage);
        }
    }

    Duration retryDelayFor(int attempt) {
        return retryBaseDelay.multipliedBy(1L << Math.min(attempt - 1, 6));
    }

    private String sanitizeError(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        if (message.length() > MAXIMUM_ERROR_LENGTH) {
            return message.substring(0, MAXIMUM_ERROR_LENGTH);
        }
        return message;
    }
}
