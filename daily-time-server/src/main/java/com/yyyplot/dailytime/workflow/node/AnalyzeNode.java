package com.yyyplot.dailytime.workflow.node;

import com.yyyplot.dailytime.agent.SentenceAnalysis;
import com.yyyplot.dailytime.agent.SentenceAnalysisService;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;

import org.springframework.stereotype.Component;

@Component
public class AnalyzeNode {
    private final SentenceAnalysisService sentenceAnalysisService;

    public AnalyzeNode(SentenceAnalysisService sentenceAnalysisService) {
        this.sentenceAnalysisService = sentenceAnalysisService;
    }

    public SentenceAnalysis execute(AutomationTaskEntity task) {
        return sentenceAnalysisService.analyze(
                task.getSentence(), task.getRequestedTranslation());
    }
}
