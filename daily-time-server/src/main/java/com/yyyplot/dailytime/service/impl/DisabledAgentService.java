package com.yyyplot.dailytime.service.impl;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.dto.agent.AgentChatRequest;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.AgentService;
import com.yyyplot.dailytime.vo.AgentChatVO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "daily-time", name = "agent-enabled", havingValue = "false")
public class DisabledAgentService implements AgentService {
    @Override
    public AgentChatVO chat(AgentChatRequest request, UserContext userContext) {
        throw new BusinessException(ErrorCode.MODEL_CONFIGURATION_ERROR, "Spring AI 已禁用");
    }
}
