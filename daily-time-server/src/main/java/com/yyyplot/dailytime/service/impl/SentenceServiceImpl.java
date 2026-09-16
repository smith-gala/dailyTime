package com.yyyplot.dailytime.service.impl;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.common.util.SentenceNormalizer;
import com.yyyplot.dailytime.entity.SentenceEntity;
import com.yyyplot.dailytime.enums.MusicTag;
import com.yyyplot.dailytime.enums.SentenceStatus;
import com.yyyplot.dailytime.mapper.SentenceMapper;
import com.yyyplot.dailytime.service.SentenceService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SentenceServiceImpl implements SentenceService {
    private final SentenceMapper sentenceMapper;

    public SentenceServiceImpl(SentenceMapper sentenceMapper) {
        this.sentenceMapper = sentenceMapper;
    }

    @Override
    @Transactional
    public SentenceEntity findOrCreate(String english, String chinese) {
        String normalizedEnglish = SentenceNormalizer.normalize(english);
        SentenceEntity existing = sentenceMapper.findByNormalized(normalizedEnglish);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        SentenceEntity sentence = new SentenceEntity();
        sentence.setEnglish(english.trim());
        sentence.setNormalizedEnglish(normalizedEnglish);
        sentence.setChinese(chinese == null ? "" : chinese.trim());
        sentence.setMusicTag(MusicTag.HAPPY_DAILY);
        sentence.setStatus(SentenceStatus.PENDING);
        sentence.setCreatedAt(now);
        sentence.setUpdatedAt(now);
        sentence.setVersion(0);
        try {
            sentenceMapper.insert(sentence);
            return sentence;
        } catch (DuplicateKeyException duplicateKeyException) {
            return sentenceMapper.findByNormalized(normalizedEnglish);
        }
    }

    @Override
    public SentenceEntity nextAvailable() {
        SentenceEntity sentence = sentenceMapper.findNextAvailable();
        if (sentence == null) {
            throw new BusinessException(ErrorCode.SENTENCE_NOT_FOUND, "没有可用的待处理句子");
        }
        return sentence;
    }
}
