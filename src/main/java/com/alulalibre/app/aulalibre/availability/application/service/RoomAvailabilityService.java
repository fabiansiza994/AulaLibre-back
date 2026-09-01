package com.alulalibre.app.aulalibre.availability.application.service;

import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomAvailabilityItemResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomStatusDetailResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomStatusResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomTimelineSlotResponse;
import com.alulalibre.app.aulalibre.block.application.mapper.BlockMapper;
import com.alulalibre.app.aulalibre.room.domain.enums.AvailabilityStatus;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.domain.repository.RoomRequestRepository;
import com.alulalibre.app.aulalibre.schedule.domain.model.Schedule;
import com.alulalibre.app.aulalibre.schedule.domain.repository.ScheduleRepository;
import com.alulalibre.app.aulalibre.shared.util.TimeRangeUtil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place that computes room availability/occupation from Schedule +
 * approved RoomRequest records for a date. Availability is never persisted
 * (no {@code Room.available}, no {@code room_availability} table) — every
 * caller (the 4 read endpoints in BACKEND_API_CONTRACT.md §6, and
 * RoomRequestService before creating/approving a request) goes through this
 * class so the overlap rule lives in exactly one place.
 */
@Service
public class RoomAvailabilityService {

    private static final LocalTime DAY_START = LocalTime.of(7, 0);
    private static final LocalTime DAY_END = LocalTime.of(22, 0);

    private static final String SOURCE_CLASS = "clase";
    private static final String SOURCE_REQUEST = "solicitud";

    private final ScheduleRepository scheduleRepository;
    private final RoomRequestRepository roomRequestRepository;
    private final BlockMapper blockMapper;

    public RoomAvailabilityService(ScheduleRepository scheduleRepository, RoomRequestRepository roomRequestRepository,
            BlockMapper blockMapper) {
        this.scheduleRepository = scheduleRepository;
        this.roomRequestRepository = roomRequestRepository;
        this.blockMapper = blockMapper;
    }

    /** Used by RoomRequestService before create/approve — must see fresh data, hence no caching. */
    @Transactional(readOnly = true)
    public boolean isRangeFree(Room room, LocalDate date, LocalTime start, LocalTime end) {
        return occupiedBlocksForDate(room, date).stream()
                .noneMatch(block -> TimeRangeUtil.overlaps(start, end, block.start(), block.end()));
    }

    @Transactional(readOnly = true)
    public RoomAvailabilityItemResponse describeForRange(Room room, LocalDate date, LocalTime start, LocalTime end) {
        List<OccupiedBlock> blocks = occupiedBlocksForDate(room, date);
        List<OccupiedBlock> overlapping = blocks.stream()
                .filter(block -> TimeRangeUtil.overlaps(start, end, block.start(), block.end()))
                .toList();

        AvailabilityStatus status;
        LocalTime availableUntil;
        if (!overlapping.isEmpty()) {
            status = AvailabilityStatus.OCCUPIED;
            availableUntil = overlapping.stream().map(OccupiedBlock::end).max(LocalTime::compareTo).orElseThrow();
        } else {
            status = AvailabilityStatus.AVAILABLE;
            availableUntil = nextBlockStartAtOrAfter(blocks, start);
        }

        return new RoomAvailabilityItemResponse(
                room.getId(), room.getName(), blockMapper.toSummary(room.getBlock()),
                room.getFloor(), room.getCapacity(), room.getType(), status, availableUntil);
    }

    @Transactional(readOnly = true)
    public RoomStatusResponse describeStatus(Room room, LocalDate date, LocalTime time) {
        PointStatus pointStatus = computeStatusAt(room, date, time);
        return new RoomStatusResponse(room.getId(), pointStatus.status(), pointStatus.until());
    }

    @Transactional(readOnly = true)
    public RoomStatusDetailResponse describeStatusDetail(Room room, LocalDate date, LocalTime time) {
        PointStatus pointStatus = computeStatusAt(room, date, time);
        return new RoomStatusDetailResponse(
                pointStatus.status(), pointStatus.until(), pointStatus.label(), pointStatus.source());
    }

    @Transactional(readOnly = true)
    public List<RoomTimelineSlotResponse> describeTimeline(Room room, LocalDate date) {
        List<OccupiedBlock> blocks = occupiedBlocksForDate(room, date);
        List<RoomTimelineSlotResponse> slots = new ArrayList<>();
        LocalTime cursor = DAY_START;

        for (OccupiedBlock block : blocks) {
            LocalTime effectiveStart = max(block.start(), DAY_START);
            LocalTime effectiveEnd = min(block.end(), DAY_END);
            if (!effectiveStart.isBefore(effectiveEnd)) {
                // Block falls entirely outside [DAY_START, DAY_END) once clipped — skip it.
                continue;
            }
            if (cursor.isBefore(effectiveStart)) {
                slots.add(new RoomTimelineSlotResponse(cursor, effectiveStart, AvailabilityStatus.AVAILABLE, null, null));
            }
            if (cursor.isBefore(effectiveEnd)) {
                slots.add(new RoomTimelineSlotResponse(
                        effectiveStart, effectiveEnd, AvailabilityStatus.OCCUPIED, block.label(), block.source()));
                cursor = effectiveEnd;
            }
        }
        if (cursor.isBefore(DAY_END)) {
            slots.add(new RoomTimelineSlotResponse(cursor, DAY_END, AvailabilityStatus.AVAILABLE, null, null));
        }
        return slots;
    }

    private PointStatus computeStatusAt(Room room, LocalDate date, LocalTime time) {
        List<OccupiedBlock> blocks = occupiedBlocksForDate(room, date);
        return blocks.stream()
                .filter(block -> !time.isBefore(block.start()) && time.isBefore(block.end()))
                .findFirst()
                .map(block -> new PointStatus(AvailabilityStatus.OCCUPIED, block.end(), block.label(), block.source()))
                .orElseGet(() -> new PointStatus(
                        AvailabilityStatus.AVAILABLE, nextBlockStartAfter(blocks, time), null, null));
    }

    private LocalTime nextBlockStartAtOrAfter(List<OccupiedBlock> blocks, LocalTime reference) {
        return blocks.stream()
                .map(OccupiedBlock::start)
                .filter(start -> !start.isBefore(reference))
                .min(LocalTime::compareTo)
                .orElse(DAY_END);
    }

    private LocalTime nextBlockStartAfter(List<OccupiedBlock> blocks, LocalTime reference) {
        return blocks.stream()
                .map(OccupiedBlock::start)
                .filter(start -> start.isAfter(reference))
                .min(LocalTime::compareTo)
                .orElse(DAY_END);
    }

    private List<OccupiedBlock> occupiedBlocksForDate(Room room, LocalDate date) {
        List<OccupiedBlock> blocks = new ArrayList<>();
        for (Schedule schedule : scheduleRepository.findByRoomIdAndDayOfWeekAndActiveTrue(room.getId(), date.getDayOfWeek())) {
            blocks.add(new OccupiedBlock(schedule.getStartTime(), schedule.getEndTime(), schedule.getSubject(), SOURCE_CLASS));
        }
        for (RoomRequest request : roomRequestRepository.findByRoomIdAndDateAndStatus(room.getId(), date, RoomRequestStatus.APPROVED)) {
            blocks.add(new OccupiedBlock(
                    request.getStartTime(), request.getEndTime(), request.getReason().getLabel(), SOURCE_REQUEST));
        }
        blocks.sort(Comparator.comparing(OccupiedBlock::start));
        return blocks;
    }

    private static LocalTime max(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime min(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }

    private record OccupiedBlock(LocalTime start, LocalTime end, String label, String source) {
    }

    private record PointStatus(AvailabilityStatus status, LocalTime until, String label, String source) {
    }
}
