package com.yyyplot.dailytime.agent.tool;

import jakarta.validation.constraints.Pattern;

public record TaskToolRequest(
        @Pattern(regexp = "^[0-9]{8}_[a-f0-9]{8}$")
        String taskId) {
}
