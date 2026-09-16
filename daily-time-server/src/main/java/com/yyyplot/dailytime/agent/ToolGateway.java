package com.yyyplot.dailytime.agent;

import com.yyyplot.dailytime.agent.tool.ConfirmedTaskToolRequest;
import com.yyyplot.dailytime.agent.tool.CreateVideoToolRequest;
import com.yyyplot.dailytime.agent.tool.TaskToolRequest;
import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.dto.task.CreateTaskCommand;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.ConversationTaskBindingService;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.vo.CreateTaskResult;
import com.yyyplot.dailytime.vo.TaskDetailVO;

import jakarta.validation.Validator;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 工具统一网关：只接受可信 ToolContext 身份，校验参数、任务归属、状态权限和原始消息确认后调用业务服务。
 */
@Component
public class ToolGateway {
    private final TaskService taskService;
    private final ToolPermissionPolicy permissionPolicy;
    private final ExplicitConfirmationDetector confirmationDetector;
    private final ConversationTaskBindingService bindingService;
    private final Validator validator;

    public ToolGateway(
            TaskService taskService,
            ToolPermissionPolicy permissionPolicy,
            ExplicitConfirmationDetector confirmationDetector,
            ConversationTaskBindingService bindingService,
            Validator validator) {
        this.taskService = taskService;
        this.permissionPolicy = permissionPolicy;
        this.confirmationDetector = confirmationDetector;
        this.bindingService = bindingService;
        this.validator = validator;
    }

    public CreateTaskResult create(CreateVideoToolRequest request, ToolContext toolContext) {
        permissionPolicy.requireKnown("create_daily_video");
        validate(request);
        if (!request.useNext() && (request.sentence() == null || request.sentence().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "需要提供英文句子或选择下一条");
        }
        String originalMessage = contextString(toolContext, "originalUserMessage");
        boolean confirmedAdopted =
                request.confirmedAdopted()
                        && confirmationDetector.recreatesAdoptedSentence(originalMessage);
        CreateTaskResult result =
                taskService.createTask(
                        new CreateTaskCommand(
                                request.sentence(),
                                request.translation(),
                                request.useNext(),
                                confirmedAdopted),
                        user(toolContext));
        bind(toolContext, result.taskId());
        return result;
    }

    public TaskDetailVO status(TaskToolRequest request, ToolContext toolContext) {
        validate(request);
        TaskToolRequest effective = new TaskToolRequest(resolveTaskId(request.taskId(), toolContext));
        validate(effective);
        TaskDetailVO task = taskService.getTask(effective.taskId(), user(toolContext));
        permissionPolicy.requireAllowed("get_task_status", task.status());
        bind(toolContext, task.taskId());
        return task;
    }

    public TaskDetailVO approve(
            ConfirmedTaskToolRequest request,
            ToolContext toolContext) {
        validate(request);
        ConfirmedTaskToolRequest effective =
                new ConfirmedTaskToolRequest(resolveTaskId(request.taskId(), toolContext));
        validate(effective);
        String originalMessage = contextString(toolContext, "originalUserMessage");
        if (!confirmationDetector.approves(originalMessage)) {
            throw new BusinessException(
                    ErrorCode.TOOL_CONFIRMATION_REQUIRED, "审核通过需要用户原始消息明确表达“通过”或“采用”");
        }
        UserContext user = user(toolContext);
        TaskDetailVO current = taskService.getTask(effective.taskId(), user);
        permissionPolicy.requireAllowed("approve_task", current.status());
        TaskDetailVO result = taskService.approveTask(effective.taskId(), user);
        bind(toolContext, result.taskId());
        return result;
    }

    public TaskDetailVO cancel(
            ConfirmedTaskToolRequest request,
            ToolContext toolContext) {
        validate(request);
        ConfirmedTaskToolRequest effective =
                new ConfirmedTaskToolRequest(resolveTaskId(request.taskId(), toolContext));
        validate(effective);
        String originalMessage = contextString(toolContext, "originalUserMessage");
        if (!confirmationDetector.cancels(originalMessage)) {
            throw new BusinessException(
                    ErrorCode.TOOL_CONFIRMATION_REQUIRED, "取消需要用户原始消息明确表达“取消”或“不采用”");
        }
        UserContext user = user(toolContext);
        TaskDetailVO current = taskService.getTask(effective.taskId(), user);
        permissionPolicy.requireAllowed("cancel_task", current.status());
        TaskDetailVO result = taskService.cancelTask(effective.taskId(), user);
        bind(toolContext, result.taskId());
        return result;
    }

    private UserContext user(ToolContext toolContext) {
        Map<String, Object> context = toolContext.getContext();
        String userId = contextString(toolContext, "userId");
        boolean administrator = Boolean.TRUE.equals(context.get("administrator"));
        return new UserContext(userId, administrator);
    }

    private String resolveTaskId(String requestedTaskId, ToolContext toolContext) {
        if (requestedTaskId != null && !requestedTaskId.isBlank()) {
            return requestedTaskId;
        }
        Object latestTaskId = toolContext.getContext().get("latestTaskId");
        if (latestTaskId == null || latestTaskId.toString().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前会话还没有可用的最近任务");
        }
        return latestTaskId.toString();
    }

    private void bind(ToolContext toolContext, String taskId) {
        bindingService.bind(
                contextString(toolContext, "conversationId"),
                contextString(toolContext, "userId"),
                taskId);
    }

    private String contextString(ToolContext toolContext, String key) {
        Object value = toolContext.getContext().get(key);
        if (value == null || value.toString().isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少可信工具上下文：" + key);
        }
        return value.toString();
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "工具参数不合法");
        }
    }
}
