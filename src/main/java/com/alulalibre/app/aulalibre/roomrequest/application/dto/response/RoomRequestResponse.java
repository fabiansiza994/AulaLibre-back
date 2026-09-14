package com.alulalibre.app.aulalibre.roomrequest.application.dto.response;

import com.alulalibre.app.aulalibre.room.application.dto.response.RoomSummaryResponse;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * {@code professorId}/{@code professorName} are always present (flat, not
 * nested — BACKEND_API_CONTRACT.md §7.5). The "my requests" view (§7.1) does
 * not strictly need them since it's always the caller's own data, but
 * including them there too is harmless (the frontend ignores extra fields)
 * and avoids maintaining two near-identical response shapes.
 */
public record RoomRequestResponse(
        Long id,
        Long professorId,
        String professorName,
        RoomSummaryResponse room,
        LocalDate date,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime start,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime end,
        RoomRequestReason reason,
        String note,
        RoomRequestStatus status,
        String reviewNote,
        LocalDateTime createdAt) {
}
