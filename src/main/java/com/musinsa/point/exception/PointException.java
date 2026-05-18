package com.musinsa.point.exception;

import lombok.Getter;

@Getter
public class PointException extends RuntimeException {

    private final PointErrorCode errorCode;

    public PointException(PointErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
