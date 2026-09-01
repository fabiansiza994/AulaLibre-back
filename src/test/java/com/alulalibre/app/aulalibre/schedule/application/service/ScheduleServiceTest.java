package com.alulalibre.app.aulalibre.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alulalibre.app.aulalibre.room.application.service.RoomService;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.schedule.application.dto.request.CreateScheduleRequest;
import com.alulalibre.app.aulalibre.schedule.application.dto.response.ScheduleResponse;
import com.alulalibre.app.aulalibre.schedule.application.mapper.ScheduleMapper;
import com.alulalibre.app.aulalibre.schedule.domain.model.Schedule;
import com.alulalibre.app.aulalibre.schedule.domain.repository.ScheduleRepository;
import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ValidationException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private RoomService roomService;

    private ScheduleService scheduleService;

    @Test
    void create_rejectsWhenStartTimeIsNotBeforeEndTime() {
        scheduleService = new ScheduleService(scheduleRepository, scheduleMapper, roomService);
        CreateScheduleRequest request = new CreateScheduleRequest(
                "lunes", LocalTime.of(18, 0), LocalTime.of(18, 0), "Programación II");

        assertThatThrownBy(() -> scheduleService.create(1L, request))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TIME_RANGE);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void create_rejectsWhenItOverlapsAnExistingBlockSameRoomAndDay() {
        scheduleService = new ScheduleService(scheduleRepository, scheduleMapper, roomService);
        CreateScheduleRequest request = new CreateScheduleRequest(
                "lunes", LocalTime.of(15, 0), LocalTime.of(17, 0), "Cálculo");

        Room room = new Room();
        room.setId(1L);
        when(roomService.findEntityById(1L)).thenReturn(room);

        Schedule existing = new Schedule();
        existing.setStartTime(LocalTime.of(14, 0));
        existing.setEndTime(LocalTime.of(16, 0));
        when(scheduleRepository.findByRoomIdAndDayOfWeekAndActiveTrue(1L, DayOfWeek.MONDAY))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> scheduleService.create(1L, request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_CONFLICT);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void create_savesWhenTimeRangeIsValidAndNoOverlap() {
        scheduleService = new ScheduleService(scheduleRepository, scheduleMapper, roomService);
        CreateScheduleRequest request = new CreateScheduleRequest(
                "lunes", LocalTime.of(18, 0), LocalTime.of(20, 0), "Programación II");

        Room room = new Room();
        room.setId(1L);
        when(roomService.findEntityById(1L)).thenReturn(room);
        when(scheduleRepository.findByRoomIdAndDayOfWeekAndActiveTrue(1L, DayOfWeek.MONDAY))
                .thenReturn(List.of());
        Schedule saved = new Schedule();
        saved.setId(10L);
        when(scheduleRepository.save(any(Schedule.class))).thenReturn(saved);
        ScheduleResponse expected = new ScheduleResponse(10L, 1L, "lunes",
                LocalTime.of(18, 0), LocalTime.of(20, 0), "Programación II");
        when(scheduleMapper.toResponse(saved)).thenReturn(expected);

        ScheduleResponse response = scheduleService.create(1L, request);

        assertThat(response).isEqualTo(expected);
    }
}
