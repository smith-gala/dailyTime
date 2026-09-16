package com.yyyplot.dailytime.agent.tool;

import jakarta.validation.constraints.Size;

public record CreateVideoToolRequest(
        @Size(max = 160)
        String sentence,
        @Size(max = 80)
        String translation,
        boolean useNext,
        boolean confirmedAdopted) {
}
