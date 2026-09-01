package com.alulalibre.app.aulalibre.auth.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Real credentials ({@code email}/{@code password}), not the {@code {role}}
 * demo-login body BACKEND_API_CONTRACT.md §2.1 documents — that shape is
 * explicitly marked there as provisional ("debe rediseñarse antes de
 * producción real"), and this is that redesign. See
 * SECURITY_IMPLEMENTATION_PLAN.md §2.
 */
public record LoginRequest(

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
