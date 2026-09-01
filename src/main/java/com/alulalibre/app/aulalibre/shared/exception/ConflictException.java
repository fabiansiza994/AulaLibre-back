package com.alulalibre.app.aulalibre.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation cannot proceed because it collides with existing
 * state (duplicate code, overlapping schedule, unavailable room, etc).
 * Always resolves to HTTP 409.
 */
public class ConflictException extends BusinessException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.CONFLICT, message);
    }
}
