package com.alulalibre.app.aulalibre.roomrequest.application.service;

import com.alulalibre.app.aulalibre.availability.application.service.RoomAvailabilityService;
import com.alulalibre.app.aulalibre.room.application.service.RoomService;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.room.domain.repository.RoomRepository;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.request.CreateRoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.request.RejectRoomRequestRequest;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.response.RoomRequestCountsResponse;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.response.RoomRequestResponse;
import com.alulalibre.app.aulalibre.roomrequest.application.mapper.RoomRequestMapper;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.domain.repository.RoomRequestRepository;
import com.alulalibre.app.aulalibre.roomrequest.domain.repository.RoomRequestSpecifications;
import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ForbiddenOperationException;
import com.alulalibre.app.aulalibre.shared.exception.ResourceNotFoundException;
import com.alulalibre.app.aulalibre.shared.exception.ValidationException;
import com.alulalibre.app.aulalibre.shared.response.PageResponse;
import com.alulalibre.app.aulalibre.shared.util.SpecificationUtil;
import com.alulalibre.app.aulalibre.user.application.service.CurrentUserProvider;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomRequestService {

    private final RoomRequestRepository roomRequestRepository;
    private final RoomRequestMapper roomRequestMapper;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomAvailabilityService availabilityService;
    private final CurrentUserProvider currentUserProvider;

    public RoomRequestService(RoomRequestRepository roomRequestRepository, RoomRequestMapper roomRequestMapper,
            RoomService roomService, RoomRepository roomRepository, RoomAvailabilityService availabilityService,
            CurrentUserProvider currentUserProvider) {
        this.roomRequestRepository = roomRequestRepository;
        this.roomRequestMapper = roomRequestMapper;
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.availabilityService = availabilityService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public RoomRequestResponse create(CreateRoomRequest request) {
        validateTimeRange(request.start(), request.end());

        // Role is enforced by @PreAuthorize("hasRole('PROFESSOR')") on the controller.
        User professor = currentUserProvider.getCurrentUser();

        Room room = roomService.findEntityById(request.roomId());
        // The frontend may have shown this room as free a while ago — re-check now, the backend is the source of truth.
        if (!availabilityService.isRangeFree(room, request.date(), request.start(), request.end())) {
            throw new ConflictException(ErrorCode.ROOM_NOT_AVAILABLE,
                    "El salón ya está ocupado en la fecha y horario solicitados");
        }

        RoomRequest roomRequest = new RoomRequest();
        roomRequest.setProfessor(professor);
        roomRequest.setRoom(room);
        roomRequest.setDate(request.date());
        roomRequest.setStartTime(request.start());
        roomRequest.setEndTime(request.end());
        roomRequest.setReason(request.reason());
        roomRequest.setNote(request.note());
        roomRequest.setStatus(RoomRequestStatus.PENDING);
        return roomRequestMapper.toResponse(roomRequestRepository.save(roomRequest));
    }

    @Transactional
    public RoomRequestResponse cancel(Long id) {
        User professor = currentUserProvider.getCurrentUser();
        RoomRequest roomRequest = findEntityById(id);
        if (!roomRequest.getProfessor().getId().equals(professor.getId())) {
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN_OPERATION,
                    "Solo el profesor dueño de la solicitud puede cancelarla");
        }
        if (roomRequest.getStatus() != RoomRequestStatus.PENDING) {
            throw new ConflictException(ErrorCode.INVALID_REQUEST_STATE,
                    "Solo se puede cancelar una solicitud en estado pendiente");
        }
        roomRequest.setStatus(RoomRequestStatus.CANCELLED);
        return roomRequestMapper.toResponse(roomRequest);
    }

    /**
     * Locks the Room row (not the RoomRequest row) before re-validating
     * availability: two concurrent approvals for different PENDING requests
     * on the *same room* serialize on that lock, so the second one always
     * re-checks against the first one's already-committed approval instead
     * of a stale "still free" snapshot. {@link #findRoomIdForLock} is a
     * scalar query specifically so this RoomRequest isn't loaded into the
     * persistence context before the lock is acquired — the load below is
     * guaranteed fresh.
     */
    @Transactional
    public RoomRequestResponse approve(Long id) {
        // Role is enforced by @PreAuthorize("hasRole('ADMIN')") on the controller.
        User admin = currentUserProvider.getCurrentUser();

        Long roomId = findRoomIdForLock(id);
        roomRepository.findByIdForUpdate(roomId);

        RoomRequest roomRequest = findEntityById(id);
        requirePending(roomRequest);
        if (!availabilityService.isRangeFree(roomRequest.getRoom(), roomRequest.getDate(),
                roomRequest.getStartTime(), roomRequest.getEndTime())) {
            throw new ConflictException(ErrorCode.ROOM_NOT_AVAILABLE,
                    "El salón ya no está disponible en ese horario (otra solicitud fue aprobada primero)");
        }

        roomRequest.setStatus(RoomRequestStatus.APPROVED);
        roomRequest.setReviewedBy(admin);
        roomRequest.setReviewedAt(LocalDateTime.now());
        return roomRequestMapper.toResponse(roomRequest);
    }

    @Transactional
    public RoomRequestResponse reject(Long id, RejectRoomRequestRequest request) {
        // Role is enforced by @PreAuthorize("hasRole('ADMIN')") on the controller.
        User admin = currentUserProvider.getCurrentUser();

        RoomRequest roomRequest = findEntityById(id);
        requirePending(roomRequest);

        roomRequest.setStatus(RoomRequestStatus.REJECTED);
        roomRequest.setReviewedBy(admin);
        roomRequest.setReviewedAt(LocalDateTime.now());
        roomRequest.setReviewNote(request.reviewNote());
        return roomRequestMapper.toResponse(roomRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomRequestResponse> findMy(Pageable pageable, RoomRequestStatus status, String search,
            LocalDate dateFrom, LocalDate dateTo, Long roomId, RoomRequestReason reason) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "dateFrom no puede ser posterior a dateTo");
        }
        User professor = currentUserProvider.getCurrentUser();
        Specification<RoomRequest> spec = myRequestsSpecification(professor.getId(), status, search, dateFrom, dateTo,
                roomId, reason);
        return PageResponse.of(roomRequestRepository.findAll(spec, pageable).map(roomRequestMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RoomRequestCountsResponse findMyCounts(String search, LocalDate dateFrom, LocalDate dateTo, Long roomId,
            RoomRequestReason reason) {
        User professor = currentUserProvider.getCurrentUser();
        Specification<RoomRequest> base = myRequestsSpecification(professor.getId(), null, search, dateFrom, dateTo,
                roomId, reason);
        return new RoomRequestCountsResponse(
                roomRequestRepository.count(base),
                roomRequestRepository.count(base.and(RoomRequestSpecifications.status(RoomRequestStatus.PENDING))),
                roomRequestRepository.count(base.and(RoomRequestSpecifications.status(RoomRequestStatus.APPROVED))),
                roomRequestRepository.count(base.and(RoomRequestSpecifications.status(RoomRequestStatus.REJECTED))),
                roomRequestRepository.count(base.and(RoomRequestSpecifications.status(RoomRequestStatus.CANCELLED))));
    }

    /** Admin inbox (§7.5) — also reused, with different status/sort/limit, as the dashboard's top-N queries (§7.6). */
    @Transactional(readOnly = true)
    public List<RoomRequestResponse> findForAdmin(RoomRequestStatus status, LocalDate dateFrom, Sort sort,
            Integer limit) {
        Specification<RoomRequest> spec = SpecificationUtil.combine(
                RoomRequestSpecifications.status(status),
                RoomRequestSpecifications.dateFrom(dateFrom));
        Sort effectiveSort = sort == null || sort.isUnsorted() ? Sort.by(Sort.Direction.DESC, "createdAt") : sort;

        List<RoomRequest> results = limit != null
                ? roomRequestRepository.findAll(spec, org.springframework.data.domain.PageRequest.of(0, limit, effectiveSort)).getContent()
                : roomRequestRepository.findAll(spec, effectiveSort);
        return results.stream().map(roomRequestMapper::toResponse).toList();
    }

    private Specification<RoomRequest> myRequestsSpecification(Long professorId, RoomRequestStatus status,
            String search, LocalDate dateFrom, LocalDate dateTo, Long roomId, RoomRequestReason reason) {
        return SpecificationUtil.combine(
                RoomRequestSpecifications.professorId(professorId),
                RoomRequestSpecifications.status(status),
                RoomRequestSpecifications.search(search),
                RoomRequestSpecifications.dateFrom(dateFrom),
                RoomRequestSpecifications.dateTo(dateTo),
                RoomRequestSpecifications.roomId(roomId),
                RoomRequestSpecifications.reason(reason));
    }

    private Long findRoomIdForLock(Long roomRequestId) {
        return roomRequestRepository.findRoomIdById(roomRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROOM_REQUEST_NOT_FOUND,
                        "La solicitud de salón no existe"));
    }

    private RoomRequest findEntityById(Long id) {
        return roomRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROOM_REQUEST_NOT_FOUND,
                        "La solicitud de salón no existe"));
    }

    private void requirePending(RoomRequest roomRequest) {
        if (roomRequest.getStatus() != RoomRequestStatus.PENDING) {
            throw new ConflictException(ErrorCode.INVALID_REQUEST_STATE,
                    "La solicitud ya no está en estado pendiente");
        }
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ValidationException(ErrorCode.INVALID_TIME_RANGE,
                    "La hora de inicio debe ser anterior a la hora de fin");
        }
    }
}
