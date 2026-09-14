package com.alulalibre.app.aulalibre.roomrequest.application.mapper;

import com.alulalibre.app.aulalibre.room.application.mapper.RoomMapper;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.response.RoomRequestResponse;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import org.springframework.stereotype.Component;

@Component
public class RoomRequestMapper {

    private final RoomMapper roomMapper;

    public RoomRequestMapper(RoomMapper roomMapper) {
        this.roomMapper = roomMapper;
    }

    public RoomRequestResponse toResponse(RoomRequest roomRequest) {
        return new RoomRequestResponse(
                roomRequest.getId(),
                roomRequest.getProfessor().getId(),
                roomRequest.getProfessor().getFirstName() + " " + roomRequest.getProfessor().getLastName(),
                roomMapper.toSummary(roomRequest.getRoom()),
                roomRequest.getDate(),
                roomRequest.getStartTime(),
                roomRequest.getEndTime(),
                roomRequest.getReason(),
                roomRequest.getNote(),
                roomRequest.getStatus(),
                roomRequest.getReviewNote(),
                roomRequest.getCreatedAt());
    }
}
