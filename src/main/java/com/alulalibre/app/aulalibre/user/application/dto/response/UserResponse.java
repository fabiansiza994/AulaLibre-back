package com.alulalibre.app.aulalibre.user.application.dto.response;

import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import java.time.LocalDateTime;

/**
 * Account-management shape (USER_MANAGEMENT_BACKEND_CONTRACT.md §3) —
 * separate first/last name, {@code active}, timestamps. Independent of
 * {@code AuthUserResponse} ({@code {id,name,role,initials,email}}), which
 * serves the session, not account administration.
 */
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        UserRole role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
