package com.alulalibre.app.aulalibre.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the caller is identified but not allowed to perform the
 * operation (wrong role, not the resource's owner). Always resolves to
 * HTTP 403 — see BACKEND_API_CONTRACT.md §1 ("403 Rol no autorizado").
 */
public class ForbiddenOperationException extends BusinessException {

    public ForbiddenOperationException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.FORBIDDEN, message);
    }
}
