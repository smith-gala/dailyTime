package com.yyyplot.dailytime.controller;

import com.yyyplot.dailytime.common.api.ApiResponse;
import com.yyyplot.dailytime.dto.agent.AgentChatRequest;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.AgentService;
import com.yyyplot.dailytime.vo.AgentChatVO;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 与智能助手对话。
     */
    @PostMapping("/chat")
    public ApiResponse<AgentChatVO> chat(
            @Valid
            @RequestBody
            AgentChatRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "local-user")
            String userId) {
        UserContext userContext = UserContext.apiUser(userId);
        AgentChatVO agentChat = agentService.chat(request, userContext);
        return ApiResponse.ok(agentChat);
    }
}
