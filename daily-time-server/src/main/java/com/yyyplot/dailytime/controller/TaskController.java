package com.yyyplot.dailytime.controller;

import com.yyyplot.dailytime.common.api.ApiResponse;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.vo.TaskDetailVO;

import jakarta.validation.constraints.Pattern;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
@Validated
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 查询任务详情。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailVO> get(
            @PathVariable
            @Pattern(regexp = "^[0-9]{8}_[a-f0-9]{8}$")
            String taskId,
            @RequestHeader(value = "X-User-Id", defaultValue = "local-user")
            String userId) {
        UserContext userContext = UserContext.apiUser(userId);
        TaskDetailVO taskDetail = taskService.getTask(taskId, userContext);
        return ApiResponse.ok(taskDetail);
    }

}
