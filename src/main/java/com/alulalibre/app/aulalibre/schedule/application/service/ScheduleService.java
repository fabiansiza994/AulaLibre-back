package com.alulalibre.app.aulalibre.schedule.application.service;

import com.alulalibre.app.aulalibre.room.application.service.RoomService;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.schedule.application.dto.request.CreateScheduleRequest;
import com.alulalibre.app.aulalibre.schedule.application.dto.response.ScheduleResponse;
import com.alulalibre.app.aulalibre.schedule.application.mapper.ScheduleDayMapper;
import com.alulalibre.app.aulalibre.schedule.application.mapper.ScheduleMapper;
import com.alulalibre.app.aulalibre.schedule.domain.model.Schedule;
import com.alulalibre.app.aulalibre.schedule.domain.repository.ScheduleRepository;
import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ResourceNotFoundException;
import com.alulalibre.app.aulalibre.shared.exception.ValidationException;
import com.alulalibre.app.aulalibre.shared.util.TimeRangeUtil;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;
    private final RoomService roomService;

    public ScheduleService(ScheduleRepository scheduleRepository, ScheduleMapper scheduleMapper,
            RoomService roomService) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleMapper = scheduleMapper;
        this.roomService = roomService;
    }

    @Transactional
    public ScheduleResponse create(Long roomId, CreateScheduleRequest request) {
        validateTimeRange(request.start(), request.end());
        Room room = roomService.findEntityById(roomId);
        DayOfWeek dayOfWeek = ScheduleDayMapper.fromKey(request.day());

        boolean overlaps = scheduleRepository.findByRoomIdAndDayOfWeekAndActiveTrue(roomId, dayOfWeek).stream()
                .anyMatch(existing -> TimeRangeUtil.overlaps(request.start(), request.end(),
                        existing.getStartTime(), existing.getEndTime()));
        if (overlaps) {
            throw new ConflictException(ErrorCode.SCHEDULE_CONFLICT,
                    "El horario se solapa con otro bloque ya asignado a este salón ese día");
        }

        Schedule schedule = new Schedule();
        schedule.setRoom(room);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setStartTime(request.start());
        schedule.setEndTime(request.end());
        schedule.setSubject(request.subject());
        return scheduleMapper.toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public void delete(Long id) {
        scheduleRepository.delete(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findByRoom(Long roomId) {
        roomService.findEntityById(roomId);
        return scheduleRepository.findByRoomId(roomId).stream().map(scheduleMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Schedule findEntityById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SCHEDULE_NOT_FOUND,
                        "El horario solicitado no existe"));
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ValidationException(ErrorCode.INVALID_TIME_RANGE,
                    "La hora de inicio debe ser anterior a la hora de fin");
        }
    }
}
