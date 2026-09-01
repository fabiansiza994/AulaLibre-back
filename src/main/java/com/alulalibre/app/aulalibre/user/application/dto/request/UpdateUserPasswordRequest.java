package com.alulalibre.app.aulalibre.user.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Administrative reset only (USER_MANAGEMENT_BACKEND_CONTRACT.md §11) — not
 * "forgot my password": no email, no recovery token, just a new temporary
 * password an admin hands the user directly.
 */
public record UpdateUserPasswordRequest(

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {
}
