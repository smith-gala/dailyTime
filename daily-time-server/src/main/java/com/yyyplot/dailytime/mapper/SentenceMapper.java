package com.yyyplot.dailytime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yyyplot.dailytime.entity.SentenceEntity;

import org.apache.ibatis.annotations.Select;

public interface SentenceMapper extends BaseMapper<SentenceEntity> {
    @Select(
            """
            SELECT
                id,
                english,
                normalized_english,
                chinese,
                music_tag,
                status,
                adopted_at,
                created_at,
                updated_at,
                version
            FROM sentence
            WHERE normalized_english = #{normalized}
            LIMIT 1
            """)
    SentenceEntity findByNormalized(String normalized);

    @Select(
            """
            SELECT
                s.id,
                s.english,
                s.normalized_english,
                s.chinese,
                s.music_tag,
                s.status,
                s.adopted_at,
                s.created_at,
                s.updated_at,
                s.version
            FROM sentence AS s
            WHERE s.status = 'PENDING'
              AND NOT EXISTS (
                SELECT 1
                FROM automation_task AS t
                WHERE t.active_key = s.normalized_english
            )
            ORDER BY s.id
            LIMIT 1
            """)
    SentenceEntity findNextAvailable();
}
