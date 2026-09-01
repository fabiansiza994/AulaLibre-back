package com.alulalibre.app.aulalibre.block.application.dto.response;

/**
 * Lightweight projection used when a {@code Block} is embedded inside
 * another module's response (e.g. RoomResponse.block).
 */
public record BlockSummaryResponse(
        Long id,
        String code,
        String name) {
}
