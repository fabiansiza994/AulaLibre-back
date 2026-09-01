package com.alulalibre.app.aulalibre.availability.application.dto.response;

import com.alulalibre.app.aulalibre.room.domain.enums.AvailabilityStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/** Item for {@code GET /rooms/{id}/timeline} — BACKEND_API_CONTRACT.md §6.4. */
public record RoomTimelineSlotResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime start,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime end,
        AvailabilityStatus status,
        String label,
        String source) {
}
