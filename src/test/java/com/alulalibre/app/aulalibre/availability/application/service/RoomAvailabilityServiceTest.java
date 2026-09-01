package com.alulalibre.app.aulalibre.availability.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomAvailabilityItemResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomStatusDetailResponse;
import com.alulalibre.app.aulalibre.availability.application.dto.response.RoomTimelineSlotResponse;
import com.alulalibre.app.aulalibre.block.application.dto.response.BlockSummaryResponse;
import com.alulalibre.app.aulalibre.block.application.mapper.BlockMapper;
import com.alulalibre.app.aulalibre.block.domain.model.Block;
import com.alulalibre.app.aulalibre.room.domain.enums.AvailabilityStatus;
import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.domain.repository.RoomRequestRepository;
import com.alulalibre.app.aulalibre.schedule.domain.model.Schedule;
import com.alulalibre.app.aulalibre.schedule.domain.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The scenario mirrors BACKEND_API_CONTRACT.md §6.4's own worked timeline
 * example: a recurring class 08:00-10:00 ("clase") plus an approved
 * tutoring request 16:00-18:00 ("solicitud") on the same room/day.
 */
@ExtendWith(MockitoExtension.class)
class RoomAvailabilityServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 31); // a Monday

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private RoomRequestRepository roomRequestRepository;
    @Mock
    private BlockMapper blockMapper;

    private RoomAvailabilityService availabilityService;
    private Room room;

    @BeforeEach
    void setUp() {
        availabilityService = new RoomAvailabilityService(scheduleRepository, roomRequestRepository, blockMapper);

        Block block = new Block();
        block.setId(2L);
        block.setCode("B2");
        block.setName("Bloque 2");

        room = new Room();
        room.setId(20L);
        room.setName("Salón 201");
        room.setBlock(block);
        room.setFloor(2);
        room.setCapacity(20);
        room.setType(RoomType.CLASSROOM);

        Schedule schedule = new Schedule();
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSubject("Programación I");
        schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setActive(true);
        lenient().when(scheduleRepository.findByRoomIdAndDayOfWeekAndActiveTrue(20L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));

        RoomRequest approvedRequest = new RoomRequest();
        approvedRequest.setStartTime(LocalTime.of(16, 0));
        approvedRequest.setEndTime(LocalTime.of(18, 0));
        approvedRequest.setReason(RoomRequestReason.TUTORING);
        lenient().when(roomRequestRepository.findByRoomIdAndDateAndStatus(20L, MONDAY, RoomRequestStatus.APPROVED))
                .thenReturn(List.of(approvedRequest));

        lenient().when(blockMapper.toSummary(any(Block.class))).thenReturn(new BlockSummaryResponse(2L, "B2", "Bloque 2"));
    }

    @Test
    void isRangeFree_isFalseWhenItOverlapsTheApprovedRequest() {
        assertThat(availabilityService.isRangeFree(room, MONDAY, LocalTime.of(15, 0), LocalTime.of(17, 0))).isFalse();
    }

    @Test
    void isRangeFree_isTrueForTheGapBetweenBlocks() {
        assertThat(availabilityService.isRangeFree(room, MONDAY, LocalTime.of(10, 0), LocalTime.of(16, 0))).isTrue();
    }

    @Test
    void describeForRange_occupied_reportsUntilTheOverlappingBlockEnds() {
        RoomAvailabilityItemResponse result = availabilityService.describeForRange(
                room, MONDAY, LocalTime.of(14, 0), LocalTime.of(18, 0));

        assertThat(result.availabilityStatus()).isEqualTo(AvailabilityStatus.OCCUPIED);
        assertThat(result.availableUntil()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void describeForRange_available_reportsUntilTheNextBlockStarts() {
        RoomAvailabilityItemResponse result = availabilityService.describeForRange(
                room, MONDAY, LocalTime.of(10, 0), LocalTime.of(16, 0));

        assertThat(result.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(result.availableUntil()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void describeStatusDetail_atAPointInsideAClass_reportsClassSource() {
        RoomStatusDetailResponse status = availabilityService.describeStatusDetail(room, MONDAY, LocalTime.of(9, 0));

        assertThat(status.status()).isEqualTo(AvailabilityStatus.OCCUPIED);
        assertThat(status.until()).isEqualTo(LocalTime.of(10, 0));
        assertThat(status.label()).isEqualTo("Programación I");
        assertThat(status.source()).isEqualTo("clase");
    }

    @Test
    void describeTimeline_matchesTheContractsWorkedExample() {
        List<RoomTimelineSlotResponse> slots = availabilityService.describeTimeline(room, MONDAY);

        assertThat(slots).containsExactly(
                new RoomTimelineSlotResponse(LocalTime.of(7, 0), LocalTime.of(8, 0), AvailabilityStatus.AVAILABLE, null, null),
                new RoomTimelineSlotResponse(LocalTime.of(8, 0), LocalTime.of(10, 0), AvailabilityStatus.OCCUPIED, "Programación I", "clase"),
                new RoomTimelineSlotResponse(LocalTime.of(10, 0), LocalTime.of(16, 0), AvailabilityStatus.AVAILABLE, null, null),
                new RoomTimelineSlotResponse(LocalTime.of(16, 0), LocalTime.of(18, 0), AvailabilityStatus.OCCUPIED, "Tutoría", "solicitud"),
                new RoomTimelineSlotResponse(LocalTime.of(18, 0), LocalTime.of(22, 0), AvailabilityStatus.AVAILABLE, null, null));
    }
}
