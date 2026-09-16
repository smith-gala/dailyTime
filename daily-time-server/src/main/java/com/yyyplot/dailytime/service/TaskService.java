package com.yyyplot.dailytime.service;

import com.yyyplot.dailytime.dto.task.CreateTaskCommand;
import com.yyyplot.dailytime.dto.task.TaskProgressCommand;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.security.UserContext;
import com.yyyplot.dailytime.vo.CreateTaskResult;
import com.yyyplot.dailytime.vo.TaskDetailVO;

import java.nio.file.Path;
import java.util.List;

public interface TaskService {
    CreateTaskResult createTask(CreateTaskCommand command, UserContext user);

    TaskDetailVO getTask(String taskId, UserContext user);

    TaskDetailVO approveTask(String taskId, UserContext user);

    TaskDetailVO cancelTask(String taskId, UserContext user);

    boolean claimTask(String taskId);

    void updateProgress(TaskProgressCommand command);

    void markAnalyzed(String taskId);

    void markDownloaded(String taskId);

    void markAwaitingReview(String taskId, Path videoFile);

    void markFailed(String taskId, Throwable error);

    AutomationTaskEntity requireEntity(String taskId);

    List<AutomationTaskEntity> findRunning();
}
