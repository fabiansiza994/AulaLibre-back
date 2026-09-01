package com.alulalibre.app.aulalibre.room.application.mapper;

import com.alulalibre.app.aulalibre.block.application.mapper.BlockMapper;
import com.alulalibre.app.aulalibre.room.application.dto.response.RoomResponse;
import com.alulalibre.app.aulalibre.room.application.dto.response.RoomSummaryResponse;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {

    private final BlockMapper blockMapper;

    public RoomMapper(BlockMapper blockMapper) {
        this.blockMapper = blockMapper;
    }

    public RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getBlock().getId(),
                room.getType(),
                room.getFloor(),
                room.getCapacity());
    }

    public RoomSummaryResponse toSummary(Room room) {
        return new RoomSummaryResponse(
                room.getId(),
                room.getName(),
                room.getFloor(),
                blockMapper.toSummary(room.getBlock()));
    }
}
