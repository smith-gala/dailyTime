package com.yyyplot.dailytime.workflow;

import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.mapper.AutomationTaskMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ConversationTaskBoundListener {
    private final AutomationTaskMapper taskMapper;
    private final TaskStateMachine taskStateMachine;

    public ConversationTaskBoundListener(
            AutomationTaskMapper taskMapper,
            TaskStateMachine taskStateMachine) {
        this.taskMapper = taskMapper;
        this.taskStateMachine = taskStateMachine;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void publishCommittedTaskSnapshot(ConversationTaskBoundEvent event) {
        // 绑定提交后补发当前状态，封闭“Worker 比首次会话绑定更快”导致的漏通知窗口。
        AutomationTaskEntity task = taskMapper.findByTaskId(event.taskId());
        if (task != null) {
            taskStateMachine.publishCurrentStatus(
                    task.getTaskId(), task.getStatus());
        }
    }
}
