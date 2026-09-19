package com.yyyplot.dailytime.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(
        @NotBlank
        @Size(max = 64)
        String conversationId,  //对话ID
        @NotBlank
        @Size(max = 1000)
        String message) {
}
