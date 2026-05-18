package com.musinsa.point.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PointException.class)
    public ResponseEntity<ErrorResponse> handlePointException(PointException e) {
        log.warn("PointException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(new ErrorResponse(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        PointErrorCode code = PointErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(new ErrorResponse(code.name(), code.getMessage()));
    }

    public record ErrorResponse(String code, String message, Instant timestamp) {
        public ErrorResponse(String code, String message) {
            this(code, message, Instant.now());
        }
    }
}
