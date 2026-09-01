package com.alulalibre.app.aulalibre.roomrequest.application.dto.request;

import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * No {@code professorId}: BACKEND_API_CONTRACT.md §7.3 is explicit that it
 * must be derived from the authenticated caller, never accepted from the
 * body (a client could otherwise forge a request as any professor). See
 * {@code CurrentUserProvider}.
 */
public record CreateRoomRequest(

        @NotNull(message = "El salón es obligatorio")
        Long roomId,

        @NotNull(message = "La fecha es obligatoria")
        @FutureOrPresent(message = "La fecha no puede ser anterior a hoy")
        LocalDate date,

        @NotNull(message = "La hora de inicio es obligatoria")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime start,

        @NotNull(message = "La hora de fin es obligatoria")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime end,

        @NotNull(message = "El motivo es obligatorio")
        RoomRequestReason reason,

        @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
        String note) {
}
