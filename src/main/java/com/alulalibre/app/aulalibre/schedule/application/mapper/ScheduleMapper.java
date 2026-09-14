package com.alulalibre.app.aulalibre.schedule.application.mapper;

import com.alulalibre.app.aulalibre.schedule.application.dto.response.ScheduleResponse;
import com.alulalibre.app.aulalibre.schedule.domain.model.Schedule;
import org.springframework.stereotype.Component;

@Component
public class ScheduleMapper {

    public ScheduleResponse toResponse(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getRoom().getId(),
                ScheduleDayMapper.toKey(schedule.getDayOfWeek()),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSubject());
    }
}
