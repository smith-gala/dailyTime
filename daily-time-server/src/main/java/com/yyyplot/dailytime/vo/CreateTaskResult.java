package com.yyyplot.dailytime.vo;

public record CreateTaskResult(
        String taskId,
        boolean created,
        String status) {
}
