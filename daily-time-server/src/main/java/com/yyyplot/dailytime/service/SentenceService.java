package com.yyyplot.dailytime.service;

import com.yyyplot.dailytime.entity.SentenceEntity;

public interface SentenceService {
    SentenceEntity findOrCreate(String english, String chinese);

    SentenceEntity nextAvailable();
}
