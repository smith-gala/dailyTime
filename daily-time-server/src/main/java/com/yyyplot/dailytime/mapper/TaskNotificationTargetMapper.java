package com.yyyplot.dailytime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yyyplot.dailytime.entity.TaskNotificationTargetEntity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskNotificationTargetMapper extends BaseMapper<TaskNotificationTargetEntity> {
    @Insert("""
            INSERT IGNORE INTO task_notification_target (
                task_id, conversation_id, user_id, created_at
            ) VALUES (
                #{taskId}, #{conversationId}, #{userId}, #{now}
            )
            """)
    int insertOnce(
            @Param("taskId") String taskId,
            @Param("conversationId") String conversationId,
            @Param("userId") String userId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT id, task_id, conversation_id, user_id, created_at
            FROM task_notification_target
            WHERE task_id = #{taskId}
            ORDER BY id
            """)
    List<TaskNotificationTargetEntity> findByTaskId(@Param("taskId") String taskId);
}
