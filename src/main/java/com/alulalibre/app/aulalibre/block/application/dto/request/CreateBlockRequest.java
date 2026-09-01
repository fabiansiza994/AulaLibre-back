package com.alulalibre.app.aulalibre.block.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBlockRequest(

        @NotBlank(message = "El código es obligatorio")
        @Size(max = 10, message = "El código no puede superar los 10 caracteres")
        String code,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String name,

        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
        String description) {
}
