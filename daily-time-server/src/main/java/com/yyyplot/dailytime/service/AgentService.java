package com.yyyplot.dailytime.service;

import com.yyyplot.dailytime.dto.agent.AgentChatRequest;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.vo.AgentChatVO;

public interface AgentService {
    AgentChatVO chat(AgentChatRequest request, UserContext user);
}
