package com.yyyplot.dailytime.agent;

import com.yyyplot.dailytime.agent.tool.ConfirmedTaskToolRequest;
import com.yyyplot.dailytime.agent.tool.CreateVideoToolRequest;
import com.yyyplot.dailytime.agent.tool.TaskToolRequest;
import com.yyyplot.dailytime.vo.CreateTaskResult;
import com.yyyplot.dailytime.vo.TaskDetailVO;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** Spring AI 工具声明入口；四个工具全部委托给统一网关。 */
@Component
public class DailyTimeTools {
    private final ToolGateway toolGateway;

    public DailyTimeTools(ToolGateway toolGateway) {
        this.toolGateway = toolGateway;
    }

    @Tool(name = "create_daily_video", description = "创建每日一句视频任务；提供英文制作指定句子，useNext=true 时选择句库下一条。")
    public CreateTaskResult create(CreateVideoToolRequest request, ToolContext toolContext) {
        return toolGateway.create(request, toolContext);
    }

    @Tool(name = "get_task_status", description = "查询任务当前状态、进度和错误；未提供 taskId 时使用当前会话最近任务。")
    public TaskDetailVO status(TaskToolRequest request, ToolContext toolContext) {
        return toolGateway.status(request, toolContext);
    }

    @Tool(name = "approve_task", description = "仅当用户原始消息明确说“通过”或“采用”时，审核通过待审核成片。")
    public TaskDetailVO approve(ConfirmedTaskToolRequest request, ToolContext toolContext) {
        return toolGateway.approve(request, toolContext);
    }

    @Tool(name = "cancel_task", description = "仅当用户原始消息明确说“取消”或“不采用”时，取消待审核成片。")
    public TaskDetailVO cancel(ConfirmedTaskToolRequest request, ToolContext toolContext) {
        return toolGateway.cancel(request, toolContext);
    }
}
