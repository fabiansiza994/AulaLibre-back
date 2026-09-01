package com.alulalibre.app.aulalibre.schedule.infrastructure.web;

import com.alulalibre.app.aulalibre.schedule.application.dto.request.CreateScheduleRequest;
import com.alulalibre.app.aulalibre.schedule.application.dto.response.ScheduleResponse;
import com.alulalibre.app.aulalibre.schedule.application.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedules are a sub-resource of Room in BACKEND_API_CONTRACT.md §5: read
 * and create live under {@code /rooms/{roomId}/schedules}, delete under the
 * flat {@code /schedules/{id}}. No update endpoint — the frontend only ever
 * adds or removes a recurring block, never edits one.
 */
@RestController
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/api/v1/rooms/{roomId}/schedules")
    public List<ScheduleResponse> findByRoom(@PathVariable Long roomId) {
        return scheduleService.findByRoom(roomId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/rooms/{roomId}/schedules")
    public ResponseEntity<ScheduleResponse> create(@PathVariable Long roomId,
            @Valid @RequestBody CreateScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(roomId, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/v1/schedules/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
