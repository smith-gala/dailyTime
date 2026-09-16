package com.yyyplot.dailytime.workflow;

import com.yyyplot.dailytime.enums.TaskStatus;

import java.time.LocalDateTime;

public record TaskStatusChangedEvent(
        String taskId,
        TaskStatus previousStatus,
        TaskStatus currentStatus,
        LocalDateTime occurredAt) {
}
