package com.yyyplot.dailytime.workflow;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.service.TaskService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

@Component
public class TaskDispatchListener {
    private final ThreadPoolTaskExecutor taskExecutor;
    private final TaskWorkflowEngine workflowEngine;
    private final TaskService taskService;

    public TaskDispatchListener(
            @Qualifier("mediaTaskExecutor")
            ThreadPoolTaskExecutor taskExecutor,
            TaskWorkflowEngine workflowEngine,
            TaskService taskService) {
        this.taskExecutor = taskExecutor;
        this.workflowEngine = workflowEngine;
        this.taskService = taskService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchQueuedTask(TaskQueuedEvent event) {
        try {
            taskExecutor.execute(() -> workflowEngine.execute(event.taskId()));
        } catch (RejectedExecutionException rejectedExecutionException) {
            BusinessException queueFullException =
                    new BusinessException(ErrorCode.TASK_EXECUTOR_REJECTED, "后台任务队列已满");
            taskService.markFailed(event.taskId(), queueFullException);
        }
    }
}
