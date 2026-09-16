-- 飞书会话最近任务：MySQL 是可恢复的事实来源，Redis 仅作为读缓存。
CREATE TABLE conversation_task_binding (
    conversation_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    latest_task_id VARCHAR(40) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_binding_task (latest_task_id),
    KEY idx_binding_user_updated (user_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

-- 每个任务的通知目标历史：会话切换到新任务后，旧任务仍能完成自己的状态通知。
CREATE TABLE task_notification_target (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(40) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_task_notification_target (task_id, conversation_id, user_id),
    KEY idx_notification_target_task (task_id, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

-- 通知 Outbox：业务状态与待发送通知在同一事务中提交，event_id 防止重复入队。
CREATE TABLE notification_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(40) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    recipient_user_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    sent_at DATETIME NULL,
    UNIQUE KEY uk_notification_event_id (event_id),
    KEY idx_notification_due (status, next_attempt_at),
    KEY idx_notification_task (task_id, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;
