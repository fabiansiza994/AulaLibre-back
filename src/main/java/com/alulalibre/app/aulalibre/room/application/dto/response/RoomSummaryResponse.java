package com.alulalibre.app.aulalibre.room.application.dto.response;

import com.alulalibre.app.aulalibre.block.application.dto.response.BlockSummaryResponse;

/**
 * Nested-block projection used when a {@code Room} is embedded inside a
 * business-endpoint response (RoomRequestResponse.room) — see
 * BACKEND_API_CONTRACT.md §7's "room: {id, name, floor, block: {...}}" shape.
 */
public record RoomSummaryResponse(
        Long id,
        String name,
        Integer floor,
        BlockSummaryResponse block) {
}
