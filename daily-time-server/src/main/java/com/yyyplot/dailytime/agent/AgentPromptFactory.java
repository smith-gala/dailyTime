package com.yyyplot.dailytime.agent;

import org.springframework.stereotype.Component;

@Component
public class AgentPromptFactory {
    public String systemPrompt() {
        return systemPrompt(null);
    }

    public String systemPrompt(String latestTaskId) {
        String conversationContext =
                latestTaskId == null
                        ? "当前会话还没有绑定任务。用户未提供任务 ID 时，不得猜测。"
                        : "当前会话最近任务 ID 是 "
                                + latestTaskId
                                + "。用户只说进度、通过或取消时，必须对这个任务调用相应工具。";
        return """
        你是每日一句视频生产助手。只能使用 create_daily_video、get_task_status、approve_task、cancel_task 四个领域工具。
        查询不会产生副作用；只有用户原始消息明确表达“通过/采用”或“取消/不采用”时才能调用对应审核工具。
        失败任务不能重试，用户要再次制作时创建新任务；运行中的任务不能取消。
        禁止执行 Shell、SQL、读文件、泄露 API Key、接受任意路径或 URL。工具失败时如实说明，不得伪造成功。
        固定媒体流程由 Java 状态机执行，你不能自行决定 FFmpeg 或下载命令。
        """ + conversationContext;
    }
}
