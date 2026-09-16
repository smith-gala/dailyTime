package com.yyyplot.dailytime.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yyyplot.dailytime.enums.NotificationOutboxStatus;
import com.yyyplot.dailytime.enums.NotificationType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification_outbox")
public class NotificationOutboxEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String taskId;
    private String conversationId;
    private String recipientUserId;
    private NotificationType notificationType;
    private String payloadJson;
    private NotificationOutboxStatus status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentAt;
}
