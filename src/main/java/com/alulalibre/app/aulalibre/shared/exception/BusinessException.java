package com.alulalibre.app.aulalibre.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for every domain/business error. Services must throw this
 * (or a subclass) instead of a generic RuntimeException so that
 * {@link GlobalExceptionHandler} can translate it into a consistent
 * {@code ApiErrorResponse}.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public BusinessException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
