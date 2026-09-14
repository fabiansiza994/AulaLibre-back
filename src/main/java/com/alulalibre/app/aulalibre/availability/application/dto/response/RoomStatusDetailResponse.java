package com.alulalibre.app.aulalibre.availability.application.dto.response;

import com.alulalibre.app.aulalibre.room.domain.enums.AvailabilityStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/**
 * Response for {@code GET /rooms/{id}/status} — BACKEND_API_CONTRACT.md §6.3.
 * {@code source} is {@code "clase"} or {@code "solicitud"} (plain string,
 * never bound from client input, so no dedicated enum).
 */
public record RoomStatusDetailResponse(
        AvailabilityStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime until,
        String label,
        String source) {
}
