package com.yyyplot.dailytime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.common.exception.GlobalExceptionHandler;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.vo.TaskDetailVO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TaskControllerTest {
    private TaskService taskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new TaskController(taskService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void returnsOnlyCurrentTaskProgress() throws Exception {
        when(taskService.getTask(anyString(), any()))
                .thenReturn(
                        new TaskDetailVO(
                                "20260902_a1b2c3d4",
                                "Keep going",
                                TaskStatus.RENDERING,
                                72,
                                "正在渲染视频",
                                null,
                                null,
                                null));

        mockMvc.perform(get("/api/v1/tasks/20260902_a1b2c3d4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RENDERING"))
                .andExpect(jsonPath("$.data.progress").value(72));
    }

    @Test
    void mapsNotFoundTo404() throws Exception {
        when(taskService.getTask(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在"));
        mockMvc.perform(get("/api/v1/tasks/20260902_a1b2c3d4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void mapsWorkerTimeoutTo504() throws Exception {
        when(taskService.getTask(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.WORKER_TIMEOUT, "超时"));
        mockMvc.perform(get("/api/v1/tasks/20260902_a1b2c3d4"))
                .andExpect(status().isGatewayTimeout());
    }
}
