package com.alulalibre.app.aulalibre.availability.application.dto.response;

import com.alulalibre.app.aulalibre.room.domain.enums.AvailabilityStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/** Item for {@code GET /rooms/status} (all rooms) — BACKEND_API_CONTRACT.md §6.2. */
public record RoomStatusResponse(
        Long id,
        AvailabilityStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime until) {
}
