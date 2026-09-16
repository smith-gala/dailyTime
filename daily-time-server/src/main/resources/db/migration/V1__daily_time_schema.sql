-- 句子库：保存候选英文句子及其采用状态。
CREATE TABLE sentence (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    english VARCHAR(160) NOT NULL,
    normalized_english VARCHAR(160) NOT NULL,
    chinese VARCHAR(80) NOT NULL DEFAULT '',
    music_tag VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    adopted_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sentence_normalized_english (normalized_english),
    KEY idx_sentence_status_id (status, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

-- 自动化任务：保存 Agent 视频生成任务的状态、阶段和 Worker 中间结果。
CREATE TABLE automation_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(40) NOT NULL,
    sentence_id BIGINT NULL,
    sentence VARCHAR(160) NOT NULL,
    normalized_sentence VARCHAR(160) NOT NULL,
    active_key VARCHAR(160) NULL,
    requested_translation VARCHAR(80) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    progress_label VARCHAR(255) NOT NULL DEFAULT '',
    requested_clip_count INT NOT NULL DEFAULT 5,
    available_results INT NOT NULL DEFAULT 0,
    run_attempt INT NOT NULL DEFAULT 1,
    analysis_json JSON NULL,
    download_json JSON NULL,
    render_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    failed_stage VARCHAR(32) NULL,
    required_action VARCHAR(64) NULL,
    worker_id VARCHAR(100) NULL,
    heartbeat_at DATETIME NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_id (task_id),
    UNIQUE KEY uk_active_sentence (active_key),
    KEY idx_task_status_created (status, created_at),
    KEY idx_task_user_created (created_by, created_at),
    CONSTRAINT fk_task_sentence FOREIGN KEY (sentence_id) REFERENCES sentence(id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

-- 任务事件：记录任务执行过程，供进度查询和问题排查使用。
CREATE TABLE task_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(40) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    progress_percent INT NOT NULL,
    message VARCHAR(500) NOT NULL,
    detail_json JSON NULL,
    created_at DATETIME NOT NULL,
    KEY idx_event_task_id (task_id, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

-- 采用记录：保存用户最终采用的视频结果。
CREATE TABLE adoption (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id VARCHAR(40) NOT NULL,
    sentence_id BIGINT NOT NULL,
    task_id VARCHAR(40) NOT NULL,
    video_file VARCHAR(255) NOT NULL,
    orientation VARCHAR(20) NOT NULL DEFAULT 'PORTRAIT',
    adopted_by VARCHAR(100) NOT NULL,
    adopted_at DATETIME NOT NULL,
    UNIQUE KEY uk_adoption_record_id (record_id),
    UNIQUE KEY uk_adoption_sentence_id (sentence_id),
    CONSTRAINT fk_adoption_sentence FOREIGN KEY (sentence_id) REFERENCES sentence(id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

-- 工具调用记录：保存 Agent 工具调用参数、结果和耗时。
CREATE TABLE tool_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    task_id VARCHAR(40) NULL,
    tool_name VARCHAR(100) NOT NULL,
    arguments_json JSON NOT NULL,
    result_json JSON NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(64) NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    KEY idx_tool_conversation (conversation_id, id),
    KEY idx_tool_task (task_id, id),
    KEY idx_tool_name_created (tool_name, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;
