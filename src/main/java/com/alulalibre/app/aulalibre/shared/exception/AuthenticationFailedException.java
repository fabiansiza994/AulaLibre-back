package com.alulalibre.app.aulalibre.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by {@code AuthService#login} for any credential failure — unknown
 * email, wrong password, or inactive user all throw this with the *same*
 * generic message, so the response never reveals which case occurred (see
 * BACKEND_API_CONTRACT.md §1 and SECURITY_IMPLEMENTATION_PLAN.md §9).
 * Always resolves to HTTP 401.
 */
public class AuthenticationFailedException extends BusinessException {

    public AuthenticationFailedException(ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.UNAUTHORIZED, message);
    }
}
