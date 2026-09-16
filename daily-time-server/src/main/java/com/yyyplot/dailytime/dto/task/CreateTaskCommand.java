package com.yyyplot.dailytime.dto.task;

public record CreateTaskCommand(
        String sentence,
        String translation,
        boolean useNext,
        boolean confirmedAdopted) {
}
