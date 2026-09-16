package com.yyyplot.dailytime.vo;

import com.yyyplot.dailytime.enums.TaskStatus;

public record TaskDetailVO(
        String taskId,
        String sentence,
        TaskStatus status,
        int progress,
        String progressLabel,
        String errorCode,
        String errorMessage,
        String videoFile) {
}
