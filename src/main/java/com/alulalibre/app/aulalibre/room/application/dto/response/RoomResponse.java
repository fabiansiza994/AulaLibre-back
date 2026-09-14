package com.alulalibre.app.aulalibre.room.application.dto.response;

import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;

/**
 * Flat {@code blockId} on purpose: BACKEND_API_CONTRACT.md §4 uses this
 * shape for the CRUD endpoints (the frontend cross-references it against
 * {@code GET /blocks} already loaded in memory). The nested {@code block}
 * object only appears in the business endpoints (availability, room
 * requests) — see RoomSummaryResponse / RoomAvailabilityItemResponse.
 */
public record RoomResponse(
        Long id,
        String name,
        Long blockId,
        RoomType type,
        Integer floor,
        Integer capacity) {
}
