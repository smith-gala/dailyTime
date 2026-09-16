package com.yyyplot.dailytime.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_notification_target")
public class TaskNotificationTargetEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;
    private String conversationId;
    private String userId;
    private LocalDateTime createdAt;
}
