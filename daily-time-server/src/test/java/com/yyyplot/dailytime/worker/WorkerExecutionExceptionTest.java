package com.yyyplot.dailytime.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.yyyplot.dailytime.enums.WorkerErrorCode;

import org.junit.jupiter.api.Test;

class WorkerExecutionExceptionTest {
    @Test
    void retainsStructuredWorkerCode() {
        var error = new WorkerExecutionException(WorkerErrorCode.SEARCH_TIMEOUT, "超时");
        assertThat(error.code()).isEqualTo(WorkerErrorCode.SEARCH_TIMEOUT);
    }
}
