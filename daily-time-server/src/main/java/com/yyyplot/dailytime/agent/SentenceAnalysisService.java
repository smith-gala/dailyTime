package com.yyyplot.dailytime.agent;

public interface SentenceAnalysisService {
    SentenceAnalysis analyze(String sentence, String confirmedTranslation);
}
