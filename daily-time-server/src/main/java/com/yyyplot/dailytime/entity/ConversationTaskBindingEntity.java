package com.yyyplot.dailytime.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation_task_binding")
public class ConversationTaskBindingEntity {
    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    private String userId;
    private String latestTaskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
