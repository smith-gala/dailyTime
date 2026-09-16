package com.yyyplot.dailytime.worker.protocol;

public record WorkerProgressEvent(
        int percent,
        String message) {
}
