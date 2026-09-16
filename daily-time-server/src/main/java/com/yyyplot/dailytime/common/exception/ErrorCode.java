package com.yyyplot.dailytime.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST),
    SENTENCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
    TASK_ALREADY_RUNNING(HttpStatus.CONFLICT),
    TASK_STATE_CONFLICT(HttpStatus.CONFLICT),
    TASK_ALREADY_ADOPTED(HttpStatus.CONFLICT),
    TOOL_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    TOOL_CONFIRMATION_REQUIRED(HttpStatus.CONFLICT),
    MODEL_CONFIGURATION_ERROR(HttpStatus.BAD_GATEWAY),
    MODEL_REQUEST_FAILED(HttpStatus.BAD_GATEWAY),
    MODEL_INVALID_OUTPUT(HttpStatus.BAD_GATEWAY),
    WORKER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    WORKER_PROCESS_FAILED(HttpStatus.BAD_GATEWAY),
    INVALID_WORKER_RESPONSE(HttpStatus.BAD_GATEWAY),
    MEDIA_RENDER_FAILED(HttpStatus.BAD_GATEWAY),
    TASK_EXECUTOR_REJECTED(HttpStatus.SERVICE_UNAVAILABLE),
    WORKER_INTERRUPTED(HttpStatus.CONFLICT),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
