package com.alulalibre.app.aulalibre.room.application.dto.request;

import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String name,

        @NotNull(message = "El bloque es obligatorio")
        Long blockId,

        @NotNull(message = "El piso es obligatorio")
        @PositiveOrZero(message = "El piso debe ser mayor o igual a cero")
        Integer floor,

        @NotNull(message = "La capacidad es obligatoria")
        @Positive(message = "La capacidad debe ser mayor que cero")
        Integer capacity,

        @NotNull(message = "El tipo de salón es obligatorio")
        RoomType type) {
}
