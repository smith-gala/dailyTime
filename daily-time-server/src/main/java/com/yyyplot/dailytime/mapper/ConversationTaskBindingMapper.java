package com.yyyplot.dailytime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yyyplot.dailytime.entity.ConversationTaskBindingEntity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface ConversationTaskBindingMapper
        extends BaseMapper<ConversationTaskBindingEntity> {
    @Insert("""
            INSERT INTO conversation_task_binding (
                conversation_id, user_id, latest_task_id, created_at, updated_at
            ) VALUES (
                #{conversationId}, #{userId}, #{taskId}, #{now}, #{now}
            )
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                latest_task_id = VALUES(latest_task_id),
                updated_at = VALUES(updated_at)
            """)
    int upsert(
            @Param("conversationId") String conversationId,
            @Param("userId") String userId,
            @Param("taskId") String taskId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT conversation_id, user_id, latest_task_id, created_at, updated_at
            FROM conversation_task_binding
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    ConversationTaskBindingEntity find(
            @Param("conversationId") String conversationId,
            @Param("userId") String userId);

}
