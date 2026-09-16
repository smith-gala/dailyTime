-- 保留已执行迁移历史，通过增量迁移收敛任务表和通知类型。
ALTER TABLE automation_task
    ADD COLUMN video_file VARCHAR(255) NULL AFTER progress_label;

-- 已生成的历史成片只迁移最终文件路径，不再保留完整 Worker JSON。
UPDATE automation_task
SET video_file = JSON_UNQUOTE(JSON_EXTRACT(render_json, '$.output'))
WHERE render_json IS NOT NULL
  AND JSON_EXTRACT(render_json, '$.output') IS NOT NULL;

-- 新状态机不再识别旧的接收/等待输入状态，升级时直接失败并释放业务唯一键。
UPDATE automation_task
SET status = 'FAILED',
    active_key = NULL,
    error_code = 'WORKFLOW_SIMPLIFIED',
    error_message = '应用升级后不再恢复旧流程任务，请创建新任务',
    progress_label = '任务执行失败',
    completed_at = COALESCE(completed_at, NOW()),
    updated_at = NOW(),
    version = version + 1
WHERE status IN ('RECEIVED', 'AWAITING_INPUT');

ALTER TABLE automation_task
    DROP COLUMN stage,
    DROP COLUMN requested_clip_count,
    DROP COLUMN available_results,
    DROP COLUMN run_attempt,
    DROP COLUMN analysis_json,
    DROP COLUMN download_json,
    DROP COLUMN render_json,
    DROP COLUMN failed_stage,
    DROP COLUMN required_action,
    DROP COLUMN worker_id,
    DROP COLUMN heartbeat_at;

DELETE FROM notification_outbox
WHERE notification_type NOT IN ('AWAITING_REVIEW', 'FAILED');

DROP TABLE task_event;
DROP TABLE tool_call_record;
