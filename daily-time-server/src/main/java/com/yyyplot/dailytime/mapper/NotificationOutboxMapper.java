package com.yyyplot.dailytime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yyyplot.dailytime.entity.NotificationOutboxEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxMapper extends BaseMapper<NotificationOutboxEntity> {
    @Select("""
            SELECT id, event_id, task_id, conversation_id, recipient_user_id,
                   notification_type, payload_json, status, attempt_count,
                   next_attempt_at, last_error, created_at, updated_at, sent_at
            FROM notification_outbox
            WHERE status IN ('PENDING', 'RETRY')
              AND next_attempt_at <= #{now}
            ORDER BY id
            LIMIT #{limit}
            """)
    List<NotificationOutboxEntity> findDue(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Update("""
            UPDATE notification_outbox
            SET status = 'PROCESSING',
                attempt_count = attempt_count + 1,
                updated_at = #{now}
            WHERE event_id = #{eventId}
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at <= #{now}
            """)
    int claim(@Param("eventId") String eventId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE notification_outbox
            SET status = 'SENT', sent_at = #{now}, updated_at = #{now}, last_error = NULL
            WHERE event_id = #{eventId} AND status = 'PROCESSING'
            """)
    int markSent(@Param("eventId") String eventId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE notification_outbox
            SET status = 'RETRY', next_attempt_at = #{nextAttemptAt},
                last_error = #{lastError}, updated_at = #{now}
            WHERE event_id = #{eventId} AND status = 'PROCESSING'
            """)
    int markRetry(
            @Param("eventId") String eventId,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE notification_outbox
            SET status = 'DEAD', last_error = #{lastError}, updated_at = #{now}
            WHERE event_id = #{eventId} AND status = 'PROCESSING'
            """)
    int markDead(
            @Param("eventId") String eventId,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE notification_outbox
            SET status = 'RETRY', next_attempt_at = #{now},
                last_error = '发送进程中断，已恢复待重试', updated_at = #{now}
            WHERE status = 'PROCESSING' AND updated_at < #{staleBefore}
            """)
    int recoverStale(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now);
}
