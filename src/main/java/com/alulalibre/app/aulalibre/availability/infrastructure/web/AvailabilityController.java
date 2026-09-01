package com.alulalibre.app.aulalibre.availability.infrastructure.web;

import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomAvailabilityItemResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomAvailabilitySearchResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomStatusDetailResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomStatusResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomTimelineSlotResponse;
import com.alulalibre.app.aulalibre.availability.application.service.RoomAvailabilityService;
import com.alulalibre.app.aulalibre.block.application.service.BlockService;
import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.room.domain.repository.RoomRepository;
import com.alulalibre.app.aulalibre.room.application.service.RoomService;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ValidationException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BACKEND_API_CONTRACT.md §6 — computed, never persisted (no Room.available, no room_availability table). */
@RestController
public class AvailabilityController {

    private final RoomAvailabilityService availabilityService;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final BlockService blockService;

    public AvailabilityController(RoomAvailabilityService availabilityService, RoomRepository roomRepository,
            RoomService roomService, BlockService blockService) {
        this.availabilityService = availabilityService;
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.blockService = blockService;
    }

    /**
     * {@code @Transactional} here (unusual for a controller) because
     * {@code roomRepository.findAll()} and the per-room
     * {@code describeForRange} mapping — which touches the lazy
     * {@code Room.block} association via {@code BlockMapper.toSummary} —
     * must share one Hibernate session. With {@code spring.jpa.open-in-view=false}
     * and no transaction here, the repository call closes its own session
     * before the stream's {@code .map()} runs, and touching {@code block}
     * throws {@code LazyInitializationException: no session}.
     */
    @Transactional(readOnly = true)
    @GetMapping("/api/v1/rooms/availability")
    public RoomAvailabilitySearchResponse search(
            @RequestParam LocalDate date,
            @RequestParam LocalTime startTime,
            @RequestParam LocalTime endTime,
            @RequestParam(required = false) Long blockId,
            @RequestParam(required = false) RoomType roomType,
            @RequestParam(required = false) Integer minCapacity) {
        if (!startTime.isBefore(endTime)) {
            throw new ValidationException(ErrorCode.INVALID_TIME_RANGE,
                    "La hora de inicio debe ser anterior a la hora de fin");
        }
        if (minCapacity != null && minCapacity < 0) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "minCapacity debe ser mayor o igual a cero");
        }
        if (blockId != null) {
            blockService.findEntityById(blockId);
        }

        List<RoomAvailabilityItemResponse> rooms = roomRepository.findAll().stream()
                .filter(room -> blockId == null || room.getBlock().getId().equals(blockId))
                .filter(room -> roomType == null || room.getType() == roomType)
                .filter(room -> minCapacity == null || room.getCapacity() >= minCapacity)
                .map(room -> availabilityService.describeForRange(room, date, startTime, endTime))
                .toList();

        return new RoomAvailabilitySearchResponse(date, startTime, endTime, rooms);
    }

    @GetMapping("/api/v1/rooms/status")
    public List<RoomStatusResponse> statusForAllRooms(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalTime time) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        LocalTime effectiveTime = time != null ? time : LocalTime.now();
        return roomRepository.findAll().stream()
                .map(room -> availabilityService.describeStatus(room, effectiveDate, effectiveTime))
                .toList();
    }

    @GetMapping("/api/v1/rooms/{id}/status")
    public RoomStatusDetailResponse statusForRoom(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalTime time) {
        Room room = roomService.findEntityById(id);
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        LocalTime effectiveTime = time != null ? time : LocalTime.now();
        return availabilityService.describeStatusDetail(room, effectiveDate, effectiveTime);
    }

    @GetMapping("/api/v1/rooms/{id}/timeline")
    public List<RoomTimelineSlotResponse> timeline(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate date) {
        Room room = roomService.findEntityById(id);
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return availabilityService.describeTimeline(room, effectiveDate);
    }
}
