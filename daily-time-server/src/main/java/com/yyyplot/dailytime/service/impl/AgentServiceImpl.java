package com.yyyplot.dailytime.service.impl;

import com.yyyplot.dailytime.agent.AgentPromptFactory;
import com.yyyplot.dailytime.agent.DailyTimeTools;
import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.dto.agent.AgentChatRequest;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.AgentService;
import com.yyyplot.dailytime.service.ConversationTaskBindingService;
import com.yyyplot.dailytime.vo.AgentChatVO;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Agent 对话编排入口。
 *
 * <p>该类负责把用户消息、系统 Prompt、领域工具和可信的服务端上下文一起交给 Spring AI；真正的工具权限判断与业务执行分别下沉到
 * ToolGateway 和 TaskService，避免模型直接操作业务层。
 */
@Service
@ConditionalOnProperty(
        prefix = "daily-time",
        name = "agent-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AgentServiceImpl implements AgentService {
    // AgentServiceImpl 是单例 Service，因此构造完成后会复用这个 ChatClient 发起每次模型请求。
    private final ChatClient chatClient;
    // 暴露给 Spring AI 的四个 @Tool 方法；这些方法统一委托给 ToolGateway。
    private final DailyTimeTools dailyTimeTools;
    private final AgentPromptFactory promptFactory;
    private final ConversationTaskBindingService conversationBindingService;

    /**
     * 唯一构造器会被 Spring 自动用于依赖注入，因此不需要再写 {@code @Autowired}。
     *
     * <p>{@link ChatClient.Builder} 不是本项目手动 new 出来的：pom.xml 引入的
     * {@code spring-ai-starter-model-openai} 会读取 application.yml 中的模型配置，自动创建 ChatModel 和预配置 Builder，
     * Spring 再把 Builder 注入这里。其余参数也都是 Spring 容器中的 Bean。
     */
    public AgentServiceImpl(
            ChatClient.Builder builder,
            DailyTimeTools dailyTimeTools,
            AgentPromptFactory promptFactory,
            ConversationTaskBindingService conversationBindingService) {
        // build() 使用 Builder 中已经装配好的 ChatModel 创建 ChatClient，不需要 new ChatClient(...)。
        // 当前 Builder 没有调用 defaultAdvisors(...)，所以没有配置 Spring AI ChatMemory Advisor。
        this.chatClient = builder.build();
        this.dailyTimeTools = dailyTimeTools;
        this.promptFactory = promptFactory;
        this.conversationBindingService = conversationBindingService;
    }

    @Override
    public AgentChatVO chat(AgentChatRequest request, UserContext user) {
        try {
            // 这里只恢复“当前会话最近操作的任务 ID”，方便理解“查进度/重试/通过”等省略 taskId 的指令；
            // 它不是完整对话记忆，也不会把历史聊天消息自动发送给模型。
            String latestTaskId =
                    conversationBindingService
                            .findLatestTaskId(request.conversationId(), user.userId())
                            .orElse(null);
            String answer = requestModel(request, user, latestTaskId);
            String taskId =
                    conversationBindingService
                            .findLatestTaskId(request.conversationId(), user.userId())
                            .orElse(latestTaskId);
            return new AgentChatVO(request.conversationId(), answer, taskId);
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.MODEL_REQUEST_FAILED, "Agent 模型调用失败", exception);
        }
    }

    private String requestModel(
            AgentChatRequest request,
            UserContext user,
            String latestTaskId) {
        // ToolContext 保存服务端生成的可信上下文，不要求模型在工具参数中自行提供这些字段。
        Map<String, Object> toolContext =
                createToolContext(request, user, latestTaskId);

        return chatClient.prompt()
                // 设置系统提示词，并把最近任务 ID 告诉模型。
                .system(promptFactory.systemPrompt(latestTaskId))
                // 设置本轮用户消息。
                .user(request.message())
                // 注册模型可以调用的领域工具。
                .tools(dailyTimeTools)
                // 传递用户身份、会话 ID 和显式确认等可信上下文。
                .toolContext(toolContext)
                // 发起模型请求；模型需要时会自动调用上面的工具。
                .call()
                // 取出模型最终返回的文本。
                .content();
    }

    private Map<String, Object> createToolContext(
            AgentChatRequest request,
            UserContext user,
            String latestTaskId) {
        Map<String, Object> toolContext = new java.util.HashMap<>();
        toolContext.put("conversationId", request.conversationId());
        toolContext.put("userId", user.userId());
        toolContext.put("administrator", user.administrator());
        toolContext.put("originalUserMessage", request.message());
        if (latestTaskId != null) {
            toolContext.put("latestTaskId", latestTaskId);
        }
        // 返回不可变 Map，防止上下文在传递过程中被后续代码意外修改。
        return Map.copyOf(toolContext);
    }

}
