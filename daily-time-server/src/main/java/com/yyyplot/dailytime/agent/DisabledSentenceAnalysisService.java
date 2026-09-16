package com.yyyplot.dailytime.agent;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "daily-time", name = "agent-enabled", havingValue = "false")
public class DisabledSentenceAnalysisService implements SentenceAnalysisService {
    @Override
    public SentenceAnalysis analyze(String sentence, String translation) {
        throw new BusinessException(ErrorCode.MODEL_CONFIGURATION_ERROR, "Spring AI 已禁用");
    }
}
