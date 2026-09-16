package com.yyyplot.dailytime.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("adoption")
public class AdoptionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordId;
    private Long sentenceId;
    private String taskId;
    private String videoFile;
    private String orientation;
    private String adoptedBy;
    private LocalDateTime adoptedAt;
}
