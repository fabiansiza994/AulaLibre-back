package com.alulalibre.app.aulalibre.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested entity does not exist. Always resolves to HTTP 404.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.NOT_FOUND, message);
    }
}
