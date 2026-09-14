package com.alulalibre.app.aulalibre.availability.application.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Top-level wrapper for {@code GET /rooms/availability} — BACKEND_API_CONTRACT.md
 * §6.1. Uses {@code startTime}/{@code endTime} at this level (unlike the
 * {@code start}/{@code end} used elsewhere) because that's the literal shape
 * documented there; the contract itself flags this naming as inconsistent
 * but intentional (§13.3).
 */
public record RoomAvailabilitySearchResponse(
        LocalDate date,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,
        List<RoomAvailabilityItemResponse> rooms) {
}
