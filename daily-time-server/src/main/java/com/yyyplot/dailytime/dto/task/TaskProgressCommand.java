package com.yyyplot.dailytime.dto.task;

import com.yyyplot.dailytime.enums.TaskStatus;

public record TaskProgressCommand(
        String taskId,
        TaskStatus status,
        int progress,
        String label) {
}
