package com.yyyplot.dailytime.agent;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.enums.TaskStatus;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Agent 工具调用权限策略。
 *
 * <p>该类只负责“某个工具能不能执行”的确定性判断，不依赖大模型自行遵守 Prompt。所有校验失败都会抛出业务异常，
 * 从而在工具进入业务 Service 前中断调用。
 */
@Component
public class ToolPermissionPolicy {
    /**
     * 系统级工具白名单。
     *
     * <p>{@link Set} 表示元素不重复的集合，{@code Set.of(...)} 创建不可变集合，运行期间不能增删，避免白名单被意外修改；
     * {@code contains} 用于判断模型请求的工具名是否在集合中。
     */
    private static final Set<String> TOOL_ALLOWLIST =
            Set.of(
                    "create_daily_video",
                    "get_task_status",
                    "approve_task",
                    "cancel_task");

    /**
     * 第一层校验：拒绝系统未注册或不可信的工具名。
     *
     * @param toolName 大模型请求调用的工具名称
     */
    public void requireKnown(String toolName) {
        if (!TOOL_ALLOWLIST.contains(toolName)) {
            // 抛出统一业务异常后，后续工具实现不会被执行。
            throw new BusinessException(ErrorCode.TOOL_NOT_ALLOWED, "工具不在白名单");
        }
    }

    /**
     * 第二层校验：根据任务当前状态，限制本次允许执行的工具。
     *
     * <p>这相当于把任务状态机转换成运行时工具权限表。例如任务还在渲染时只能查询或取消，只有进入待审核状态后才能审核通过。
     *
     * @param toolName 本次请求调用的工具名称
     * @param status 从数据库查询到的任务当前状态
     */
    public void requireAllowed(String toolName, TaskStatus status) {
        // 状态授权之前先做系统级白名单校验，未知工具在任何状态下都不能执行。
        requireKnown(toolName);
        if (status == null) {
            // 没有任务上下文时只执行系统级白名单校验，适用于不依赖既有任务状态的调用。
            return;
        }

        // switch 表达式按当前状态生成本次调用的不可变工具白名单。
        Set<String> allowedTools =
                switch (status) {
                    // 运行中的 Worker 不能取消，避免后台进程继续写入已取消任务。
                    case QUEUED, ANALYZING, DOWNLOADING, RENDERING ->
                            Set.of("get_task_status");
                    case AWAITING_REVIEW ->
                            Set.of("get_task_status", "approve_task", "cancel_task");
                    case ADOPTED, CANCELED, FAILED -> Set.of("get_task_status");
                };
        if (!allowedTools.contains(toolName)) {
            throw new BusinessException(ErrorCode.TOOL_NOT_ALLOWED, "当前任务状态不允许调用该工具");
        }
    }

}
