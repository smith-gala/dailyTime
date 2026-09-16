package com.yyyplot.dailytime.worker;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkerProcessResult(
        int exitCode,
        JsonNode finalEvent) {
}
