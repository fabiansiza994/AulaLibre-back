package com.alulalibre.app.aulalibre.schedule.application.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * No {@code roomId}: BACKEND_API_CONTRACT.md §5.2 takes it from the path
 * ({@code POST /rooms/{roomId}/schedules}), not the body.
 */
public record CreateScheduleRequest(

        @NotBlank(message = "El día es obligatorio")
        String day,

        @NotNull(message = "La hora de inicio es obligatoria")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime start,

        @NotNull(message = "La hora de fin es obligatoria")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime end,

        @NotBlank(message = "La materia o actividad es obligatoria")
        @Size(max = 150, message = "La materia o actividad no puede superar los 150 caracteres")
        String subject) {
}
