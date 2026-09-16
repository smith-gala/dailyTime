package com.yyyplot.dailytime.agent;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class SentenceAnalysisValidator {
    private static final int MAXIMUM_TRANSLATION_LENGTH = 80;
    private static final int REQUIRED_EXAMPLE_COUNT = 2;

    public SentenceAnalysis validate(SentenceAnalysis analysis, String confirmedTranslation) {
        if (!isStructurallyValid(analysis)) {
            throw new BusinessException(ErrorCode.MODEL_INVALID_OUTPUT, "模型结构化内容不完整");
        }

        String trimmedTranslation = trimToNull(confirmedTranslation);
        if (trimmedTranslation != null
                && !trimmedTranslation.equals(analysis.chineseTranslation())) {
            return new SentenceAnalysis(
                    trimmedTranslation,
                    analysis.phonetic(),
                    analysis.explanation(),
                    analysis.examples(),
                    analysis.musicTag());
        }
        return analysis;
    }

    private boolean isStructurallyValid(SentenceAnalysis analysis) {
        return analysis != null
                && isValidTranslation(analysis.chineseTranslation())
                && isValidPhonetic(analysis.phonetic())
                && hasValidExamples(analysis)
                && analysis.musicTag() != null;
    }

    private boolean isValidTranslation(String translation) {
        return !isBlank(translation) && translation.length() <= MAXIMUM_TRANSLATION_LENGTH;
    }

    private boolean isValidPhonetic(String phonetic) {
        return !isBlank(phonetic) && phonetic.startsWith("/") && phonetic.endsWith("/");
    }

    private boolean hasValidExamples(SentenceAnalysis analysis) {
        if (analysis.examples() == null || analysis.examples().size() != REQUIRED_EXAMPLE_COUNT) {
            return false;
        }
        return analysis.examples().stream()
                .noneMatch(example -> isBlank(example.english()) || isBlank(example.chinese()));
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
