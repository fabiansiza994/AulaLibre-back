package com.alulalibre.app.aulalibre.roomrequest.infrastructure.web;

import com.alulalibre.app.aulalibre.roomrequest.application.dto.request.CreateRoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.request.RejectRoomRequestRequest;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.response.RoomRequestCountsResponse;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.response.RoomRequestResponse;
import com.alulalibre.app.aulalibre.roomrequest.application.service.RoomRequestService;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.shared.response.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/room-requests")
public class RoomRequestController {

    private final RoomRequestService roomRequestService;

    public RoomRequestController(RoomRequestService roomRequestService) {
        this.roomRequestService = roomRequestService;
    }

    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping("/my")
    public PageResponse<RoomRequestResponse> findMy(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) RoomRequestStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) RoomRequestReason reason) {
        return roomRequestService.findMy(pageable, status, search, dateFrom, dateTo, roomId, reason);
    }

    @PreAuthorize("hasRole('PROFESSOR')")
    @GetMapping("/my/counts")
    public RoomRequestCountsResponse findMyCounts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) RoomRequestReason reason) {
        return roomRequestService.findMyCounts(search, dateFrom, dateTo, roomId, reason);
    }

    @PreAuthorize("hasRole('PROFESSOR')")
    @PostMapping
    public ResponseEntity<RoomRequestResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomRequestService.create(request));
    }

    @PreAuthorize("hasRole('PROFESSOR')")
    @PatchMapping("/{id}/cancel")
    public RoomRequestResponse cancel(@PathVariable Long id) {
        return roomRequestService.cancel(id);
    }

    /**
     * Admin inbox — BACKEND_API_CONTRACT.md §7.5, reused with different params
     * for §7.6's dashboard queries. {@code sort} must NOT carry
     * {@code @RequestParam}: that annotation makes Spring MVC's own
     * converter-based resolver claim the parameter before Spring Data's
     * {@code SortHandlerMethodArgumentResolver} gets a chance to, and MVC has
     * no {@code String -> Sort} converter, so every request with a
     * {@code sort} param threw {@code MethodArgumentConversionNotSupportedException}.
     * Left unannotated, Spring Data resolves it directly (defaulting to
     * {@code Sort.unsorted()} when absent).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<RoomRequestResponse> findForAdmin(
            @RequestParam(required = false) RoomRequestStatus status,
            @RequestParam(required = false) LocalDate dateFrom,
            Sort sort,
            @RequestParam(required = false) Integer limit) {
        return roomRequestService.findForAdmin(status, dateFrom, sort, limit);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/approve")
    public RoomRequestResponse approve(@PathVariable Long id) {
        return roomRequestService.approve(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/reject")
    public RoomRequestResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) RejectRoomRequestRequest request) {
        return roomRequestService.reject(id, request != null ? request : new RejectRoomRequestRequest(null));
    }
}
