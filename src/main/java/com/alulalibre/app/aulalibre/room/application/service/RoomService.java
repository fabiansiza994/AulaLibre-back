package com.alulalibre.app.aulalibre.room.application.service;

import com.alulalibre.app.aulalibre.block.application.service.BlockService;
import com.alulalibre.app.aulalibre.block.domain.model.Block;
import com.alulalibre.app.aulalibre.room.application.dto.request.CreateRoomRequest;
import com.alulalibre.app.aulalibre.room.application.dto.request.UpdateRoomRequest;
import com.alulalibre.app.aulalibre.room.application.dto.response.RoomResponse;
import com.alulalibre.app.aulalibre.room.application.mapper.RoomMapper;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.room.domain.repository.RoomRepository;
import com.alulalibre.app.aulalibre.roomrequest.domain.repository.RoomRequestRepository;
import com.alulalibre.app.aulalibre.schedule.domain.repository.ScheduleRepository;
import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;
    private final BlockService blockService;
    private final ScheduleRepository scheduleRepository;
    private final RoomRequestRepository roomRequestRepository;

    public RoomService(RoomRepository roomRepository, RoomMapper roomMapper, BlockService blockService,
            ScheduleRepository scheduleRepository, RoomRequestRepository roomRequestRepository) {
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
        this.blockService = blockService;
        this.scheduleRepository = scheduleRepository;
        this.roomRequestRepository = roomRequestRepository;
    }

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {
        Block block = blockService.findEntityById(request.blockId());
        Room room = new Room();
        room.setName(request.name());
        room.setBlock(block);
        room.setFloor(request.floor());
        room.setCapacity(request.capacity());
        room.setType(request.type());
        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse update(Long id, UpdateRoomRequest request) {
        Room room = findEntityById(id);
        Block block = blockService.findEntityById(request.blockId());
        room.setName(request.name());
        room.setBlock(block);
        room.setFloor(request.floor());
        room.setCapacity(request.capacity());
        room.setType(request.type());
        return roomMapper.toResponse(room);
    }

    /**
     * Cascades the deletion to the room's recurring Schedule blocks (as
     * BACKEND_API_CONTRACT.md §4.5 requires), but — unlike the mock, which
     * deletes silently — refuses to delete a room that still has any
     * RoomRequest attached. Those are a business/audit record (who asked
     * for what, approved/rejected by whom); silently destroying them would
     * be worse than the frontend's current "don't ask" behavior.
     */
    @Transactional
    public void delete(Long id) {
        Room room = findEntityById(id);
        if (roomRequestRepository.existsByRoomId(id)) {
            throw new ConflictException(ErrorCode.ROOM_HAS_REQUESTS,
                    "No se puede eliminar el salón: todavía tiene solicitudes asociadas.");
        }
        scheduleRepository.deleteByRoomId(id);
        roomRepository.delete(room);
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(Long id) {
        return roomMapper.toResponse(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> findAll() {
        return roomRepository.findAll().stream().map(roomMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Room findEntityById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROOM_NOT_FOUND,
                        "El salón solicitado no existe"));
    }
}
