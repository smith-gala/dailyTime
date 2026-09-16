package com.yyyplot.dailytime.worker.protocol;

import java.nio.file.Path;

public record DownloadWorkerRequest(
        String taskId,
        String sentence,
        Path taskDirectory,
        int count) {
}
