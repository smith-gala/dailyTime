package com.yyyplot.dailytime.common.exception;

import com.yyyplot.dailytime.common.api.ApiResponse;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.code();
        String errorCodeName = errorCode.name();
        ApiResponse<Void> errorResponse = ApiResponse.error(errorCodeName, exception.getMessage());
        HttpStatus httpStatus = errorCode.httpStatus();
        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        ApiResponse<Void> errorResponse =
                ApiResponse.error(ErrorCode.INVALID_ARGUMENT.name(), "请求参数不合法");
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception exception) {
        LOGGER.error("未处理的服务端异常", exception);
        ApiResponse<Void> errorResponse =
                ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), "服务暂时不可用");
        return ResponseEntity.internalServerError().body(errorResponse);
    }
}
