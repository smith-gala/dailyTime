package com.yyyplot.dailytime.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.yyyplot.dailytime.enums.MusicTag;
import com.yyyplot.dailytime.enums.SentenceStatus;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sentence")
public class SentenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String english;
    private String normalizedEnglish;
    private String chinese;
    private MusicTag musicTag;
    private SentenceStatus status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime adoptedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
}
