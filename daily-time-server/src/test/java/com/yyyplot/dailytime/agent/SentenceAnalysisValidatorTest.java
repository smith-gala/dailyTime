package com.yyyplot.dailytime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.enums.MusicTag;

import org.junit.jupiter.api.Test;

import java.util.List;

class SentenceAnalysisValidatorTest {
    private final SentenceAnalysisValidator validator = new SentenceAnalysisValidator();

    private SentenceAnalysis valid() {
        return new SentenceAnalysis(
                "算了吧",
                "/fɔːrˈɡet ɪt/",
                "自然讲解",
                List.of(
                        new BilingualExample("Forget it.", "算了。"),
                        new BilingualExample("Just forget it.", "别想了。")),
                MusicTag.HAPPY_DAILY);
    }

    @Test
    void preservesConfirmedTranslation() {
        SentenceAnalysis validatedAnalysis = validator.validate(valid(), "别提了");
        String chineseTranslation = validatedAnalysis.chineseTranslation();

        assertThat(chineseTranslation).isEqualTo("别提了");
    }

    @Test
    void rejectsWrongExampleCount() {
        SentenceAnalysis invalidAnalysis =
                new SentenceAnalysis("好", "/ok/", "x", List.of(), MusicTag.HAPPY_DAILY);
        assertThatThrownBy(() -> validator.validate(invalidAnalysis, ""))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNonIpaWrapper() {
        SentenceAnalysis validAnalysis = valid();
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        new SentenceAnalysis(
                                                validAnalysis.chineseTranslation(),
                                                "abc",
                                                validAnalysis.explanation(),
                                                validAnalysis.examples(),
                                                validAnalysis.musicTag()),
                                        ""))
                .isInstanceOf(BusinessException.class);
    }
}
