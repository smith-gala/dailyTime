package com.yyyplot.dailytime.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.MDC;

public record ApiResponse<T>(
        String code,
        String message,
        @JsonProperty("data")
        T payload,
        String traceId) {

    public static <T> ApiResponse<T> ok(T payload) {
        return new ApiResponse<>("OK", "success", payload, MDC.get("traceId"));
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"));
    }
}
