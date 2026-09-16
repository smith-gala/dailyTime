package com.yyyplot.dailytime.worker.protocol;

import com.yyyplot.dailytime.agent.SentenceAnalysis;
import com.yyyplot.dailytime.enums.MusicTag;

import java.nio.file.Path;
import java.util.List;

public record RenderWorkerRequest(
        String taskId,
        String sentence,
        SentenceAnalysis content,
        List<Path> sources,
        Path taskDirectory,
        Path output,
        MusicTag musicTag) {
}
