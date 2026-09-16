package com.yyyplot.dailytime.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class FlywayMigrationTest {
    @Test
    void migrationsPreserveCoreIndexesAndRemoveLegacyTaskData() throws Exception {
        String sql =
                new String(
                        getClass()
                                .getResourceAsStream("/db/migration/V1__daily_time_schema.sql")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        assertThat(sql)
                .contains(
                        "CREATE TABLE sentence",
                        "CREATE TABLE automation_task",
                        "CREATE TABLE adoption",
                        "UNIQUE KEY uk_active_sentence (active_key)",
                        "UNIQUE KEY uk_adoption_sentence_id");

        String notificationSql =
                new String(
                        getClass()
                                .getResourceAsStream(
                                        "/db/migration/V2__feishu_conversation_and_notification_outbox.sql")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        assertThat(notificationSql)
                .contains(
                        "CREATE TABLE conversation_task_binding",
                        "CREATE TABLE task_notification_target",
                        "CREATE TABLE notification_outbox",
                        "UNIQUE KEY uk_task_notification_target",
                        "UNIQUE KEY uk_notification_event_id (event_id)",
                        "KEY idx_notification_due (status, next_attempt_at)");

        String simplificationSql =
                new String(
                        getClass()
                                .getResourceAsStream(
                                        "/db/migration/V3__simplify_video_task_workflow.sql")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        assertThat(simplificationSql)
                .contains(
                        "ADD COLUMN video_file",
                        "DROP COLUMN stage",
                        "DROP COLUMN run_attempt",
                        "DROP COLUMN analysis_json",
                        "DROP COLUMN download_json",
                        "DROP COLUMN render_json",
                        "DROP TABLE task_event",
                        "DROP TABLE tool_call_record");
    }
}
