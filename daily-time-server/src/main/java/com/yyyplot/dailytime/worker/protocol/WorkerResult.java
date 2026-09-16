package com.yyyplot.dailytime.worker.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkerResult(
        JsonNode payload) {
}
