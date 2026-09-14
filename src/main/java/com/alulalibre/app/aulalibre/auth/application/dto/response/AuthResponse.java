package com.alulalibre.app.aulalibre.auth.application.dto.response;

/** {@code POST /auth/login} response — BACKEND_API_CONTRACT.md §2.1, literal shape. */
public record AuthResponse(
        String token,
        AuthUserResponse user) {
}
