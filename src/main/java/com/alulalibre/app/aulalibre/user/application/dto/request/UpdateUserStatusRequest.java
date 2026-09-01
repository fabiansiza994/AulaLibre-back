package com.alulalibre.app.aulalibre.user.application.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(

        @NotNull(message = "El estado 'active' es obligatorio")
        Boolean active) {
}
