package com.alulalibre.app.aulalibre.auth.application.dto.response;

import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;

/**
 * Exact shape of BACKEND_API_CONTRACT.md §2.1's {@code user} object (also
 * used standalone by {@code GET /auth/me}, §2.2). No {@code passwordHash} —
 * this record simply has no field for it.
 */
public record AuthUserResponse(
        Long id,
        String name,
        UserRole role,
        String initials,
        String email) {
}
