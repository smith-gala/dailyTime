package com.yyyplot.dailytime.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TaskNotificationOutboxListener {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(TaskNotificationOutboxListener.class);

    private final ConversationTaskBindingService bindingService;
    private final AutomationTaskMapper taskMapper;
    private final NotificationOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public TaskNotificationOutboxListener(
            ConversationTaskBindingService bindingService,
            AutomationTaskMapper taskMapper,
            NotificationOutboxMapper outboxMapper,
            ObjectMapper objectMapper) {
        this.bindingService = bindingService;
        this.taskMapper = taskMapper;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.BEFORE_COMMIT)
    public void enqueue(TaskStatusChangedEvent event) {
        NotificationType notificationType = notificationType(event.currentStatus());
        if (notificationType == null) {
            return;
        }

        AutomationTaskEntity task = taskMapper.findByTaskId(event.taskId());
        if (task == null) {
            LOGGER.warn("通知事件对应任务不存在，taskId={}", event.taskId());
            return;
        }
        List<TaskNotificationTargetEntity> targets =
                bindingService.findNotificationTargets(event.taskId());
        if (targets.isEmpty()) {
            LOGGER.info("任务尚未绑定飞书会话，暂不创建通知，taskId={}", event.taskId());
            return;
        }

        String payloadJson = serializePayload(buildPayload(task, notificationType));
        for (TaskNotificationTargetEntity target : targets) {
            insertOnce(event, notificationType, target, payloadJson);
        }
    }

    private void insertOnce(
            TaskStatusChangedEvent event,
            NotificationType notificationType,
            TaskNotificationTargetEntity target,
            String payloadJson) {
        LocalDateTime now = event.occurredAt();
        NotificationOutboxEntity outbox = new NotificationOutboxEntity();
        outbox.setEventId(eventId(event, target.getConversationId()));
        outbox.setTaskId(event.taskId());
        outbox.setConversationId(target.getConversationId());
        outbox.setRecipientUserId(target.getUserId());
        outbox.setNotificationType(notificationType);
        outbox.setPayloadJson(payloadJson);
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        try {
            outboxMapper.insert(outbox);
        } catch (DuplicateKeyException duplicateEvent) {
            LOGGER.debug("重复通知事件已忽略，eventId={}", outbox.getEventId());
        }
    }

    private Map<String, Object> buildPayload(
            AutomationTaskEntity task,
            NotificationType notificationType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("sentence", task.getSentence());
        payload.put("notificationType", notificationType.name());
        payload.put("message", notificationMessage(task, notificationType));
        if (notificationType == NotificationType.AWAITING_REVIEW) {
            payload.put("media", task.getVideoFile());
        }
        if (notificationType == NotificationType.FAILED) {
            payload.put("errorCode", task.getErrorCode());
            payload.put("errorMessage", task.getErrorMessage());
        }
        return payload;
    }

    private String notificationMessage(
            AutomationTaskEntity task,
            NotificationType notificationType) {
        return switch (notificationType) {
            case AWAITING_REVIEW ->
                    "每日一句视频已生成（任务 "
                            + task.getTaskId()
                            + "）。\n句子："
                            + task.getSentence()
                            + "\n请回复：通过 / 取消";
            case FAILED ->
                    "每日一句视频任务失败（任务 "
                            + task.getTaskId()
                            + "）。\n原因："
                            + safeText(task.getErrorMessage(), "未知错误")
                            + "\n如需再次制作，请创建新任务。";
        };
    }

    private NotificationType notificationType(TaskStatus status) {
        return switch (status) {
            case AWAITING_REVIEW -> NotificationType.AWAITING_REVIEW;
            case FAILED -> NotificationType.FAILED;
            default -> null;
        };
    }

    private String eventId(TaskStatusChangedEvent event, String conversationId) {
        String idempotencySource =
                event.taskId() + "|" + event.currentStatus() + "|" + conversationId;
        return UUID.nameUUIDFromBytes(idempotencySource.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException serializationError) {
            throw new IllegalStateException("通知 Outbox 序列化失败", serializationError);
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
