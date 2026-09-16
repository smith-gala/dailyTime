package com.yyyplot.dailytime.workflow;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.service.TaskService;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskRecoveryRunner {
    private final TaskService taskService;

    public TaskRecoveryRunner(TaskService taskService) {
        this.taskService = taskService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        List<AutomationTaskEntity> interruptedTasks = taskService.findRunning();
        interruptedTasks.forEach(this::markTaskInterrupted);
    }

    private void markTaskInterrupted(AutomationTaskEntity task) {
        BusinessException interruptedException =
                new BusinessException(ErrorCode.WORKER_INTERRUPTED, "应用重启导致 Worker 中断");
        taskService.markFailed(task.getTaskId(), interruptedException);
    }
}
