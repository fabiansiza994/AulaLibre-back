package com.alulalibre.app.aulalibre.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown for business-rule validation failures that Jakarta Bean Validation
 * cannot express (e.g. startTime must be before endTime). Always resolves
 * to HTTP 400.
 */
public class ValidationException extends BusinessException {

    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.BAD_REQUEST, message);
    }
}
