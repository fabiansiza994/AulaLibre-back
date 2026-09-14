package com.alulalibre.app.aulalibre.schedule.domain.repository;

import com.alulalibre.app.aulalibre.schedule.domain.model.Schedule;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByRoomId(Long roomId);

    List<Schedule> findByRoomIdAndDayOfWeekAndActiveTrue(Long roomId, DayOfWeek dayOfWeek);

    /** Cascade used by RoomService#delete (BACKEND_API_CONTRACT.md §4.5). */
    void deleteByRoomId(Long roomId);
}
