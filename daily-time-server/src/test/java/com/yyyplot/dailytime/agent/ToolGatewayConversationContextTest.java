package com.yyyplot.dailytime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yyyplot.dailytime.agent.tool.ConfirmedTaskToolRequest;
import com.yyyplot.dailytime.agent.tool.TaskToolRequest;
import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.ConversationTaskBindingService;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.vo.TaskDetailVO;

import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;
import java.util.Set;

class ToolGatewayConversationContextTest {
    private static final String TASK_ID = "20260904_a1b2c3d4";

    @Test
    void statusUsesTrustedUserAndLatestConversationTask() {
        TaskService taskService = mock(TaskService.class);
        ConversationTaskBindingService bindingService = mock(ConversationTaskBindingService.class);
        UserContext user = UserContext.apiUser("ou_user");
        TaskDetailVO detail = detail(TaskStatus.RENDERING);
        when(taskService.getTask(TASK_ID, user)).thenReturn(detail);
        ToolGateway gateway = gateway(taskService, bindingService);

        TaskDetailVO result =
                gateway.status(new TaskToolRequest(null), context("任务进度怎么样了"));

        assertThat(result.taskId()).isEqualTo(TASK_ID);
        verify(taskService).getTask(TASK_ID, user);
        verify(bindingService).bind("oc_chat", "ou_user", TASK_ID);
    }

    @Test
    void approveRequiresActionInOriginalMessage() {
        TaskService taskService = mock(TaskService.class);
        UserContext user = UserContext.apiUser("ou_user");
        when(taskService.getTask(TASK_ID, user)).thenReturn(detail(TaskStatus.AWAITING_REVIEW));
        when(taskService.approveTask(TASK_ID, user)).thenReturn(detail(TaskStatus.ADOPTED));
        ToolGateway gateway = gateway(taskService, mock(ConversationTaskBindingService.class));

        TaskDetailVO result =
                gateway.approve(new ConfirmedTaskToolRequest(null), context("这个成片通过"));

        assertThat(result.status()).isEqualTo(TaskStatus.ADOPTED);
        verify(taskService).approveTask(TASK_ID, user);
        assertThatThrownBy(
                        () ->
                                gateway.approve(
                                        new ConfirmedTaskToolRequest(null),
                                        context("这个成片能通过吗？")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelRequiresActionInOriginalMessage() {
        TaskService taskService = mock(TaskService.class);
        UserContext user = UserContext.apiUser("ou_user");
        when(taskService.getTask(TASK_ID, user)).thenReturn(detail(TaskStatus.AWAITING_REVIEW));
        when(taskService.cancelTask(TASK_ID, user)).thenReturn(detail(TaskStatus.CANCELED));
        ToolGateway gateway = gateway(taskService, mock(ConversationTaskBindingService.class));

        TaskDetailVO result =
                gateway.cancel(new ConfirmedTaskToolRequest(null), context("这个成片不采用"));

        assertThat(result.status()).isEqualTo(TaskStatus.CANCELED);
        verify(taskService).cancelTask(TASK_ID, user);
    }

    private ToolGateway gateway(
            TaskService taskService,
            ConversationTaskBindingService bindingService) {
        Validator validator = mock(Validator.class);
        when(validator.validate(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of());
        return new ToolGateway(
                taskService,
                new ToolPermissionPolicy(),
                new ExplicitConfirmationDetector(),
                bindingService,
                validator);
    }

    private ToolContext context(String originalMessage) {
        return new ToolContext(
                Map.of(
                        "conversationId", "oc_chat",
                        "userId", "ou_user",
                        "administrator", false,
                        "originalUserMessage", originalMessage,
                        "latestTaskId", TASK_ID));
    }

    private TaskDetailVO detail(TaskStatus status) {
        return new TaskDetailVO(
                TASK_ID,
                "Keep going",
                status,
                status == TaskStatus.ADOPTED ? 100 : 95,
                "当前进度",
                null,
                null,
                status == TaskStatus.AWAITING_REVIEW ? "D:/video.mp4" : null);
    }
}
