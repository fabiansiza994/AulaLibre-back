package com.alulalibre.app.aulalibre.schedule.application.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        Long roomId,
        String day,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime start,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime end,
        String subject) {
}
