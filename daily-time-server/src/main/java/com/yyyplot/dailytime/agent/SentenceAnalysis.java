package com.yyyplot.dailytime.agent;

import com.yyyplot.dailytime.enums.MusicTag;

import java.util.List;

public record SentenceAnalysis(
        String chineseTranslation,
        String phonetic,
        String explanation,
        List<BilingualExample> examples,
        MusicTag musicTag) {
}
