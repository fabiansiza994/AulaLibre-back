package com.alulalibre.app.aulalibre.availability.application.dto.response;

import com.alulalibre.app.aulalibre.block.application.dto.response.BlockSummaryResponse;
import com.alulalibre.app.aulalibre.room.domain.enums.AvailabilityStatus;
import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/** Item inside {@link RoomAvailabilitySearchResponse#rooms()} — BACKEND_API_CONTRACT.md §6.1. */
public record RoomAvailabilityItemResponse(
        Long id,
        String name,
        BlockSummaryResponse block,
        Integer floor,
        Integer capacity,
        RoomType type,
        AvailabilityStatus availabilityStatus,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime availableUntil) {
}
