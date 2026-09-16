package com.yyyplot.dailytime.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.yyyplot.dailytime.enums.TaskStatus;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_task")
public class AutomationTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;
    private Long sentenceId;
    private String sentence;
    private String normalizedSentence;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String activeKey;

    private String requestedTranslation;
    private TaskStatus status;
    private Integer progressPercent;
    private String progressLabel;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String videoFile;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;

    @Version
    private Integer version;
}
