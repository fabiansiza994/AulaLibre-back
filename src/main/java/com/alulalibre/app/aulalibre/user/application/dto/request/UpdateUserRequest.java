package com.alulalibre.app.aulalibre.user.application.dto.request;

import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * No {@code password}/{@code active} — USER_MANAGEMENT_BACKEND_CONTRACT.md
 * §9: those are separate, dedicated actions ({@code PATCH .../status},
 * {@code PATCH .../password}), never silently changed by a general edit.
 */
public record UpdateUserRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String lastName,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        @NotNull(message = "El rol es obligatorio")
        UserRole role) {
}
