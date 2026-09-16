package com.yyyplot.dailytime.worker;

import com.yyyplot.dailytime.enums.WorkerErrorCode;

public class WorkerExecutionException extends RuntimeException {
    private final WorkerErrorCode code;

    public WorkerExecutionException(WorkerErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public WorkerErrorCode code() {
        return code;
    }
}
