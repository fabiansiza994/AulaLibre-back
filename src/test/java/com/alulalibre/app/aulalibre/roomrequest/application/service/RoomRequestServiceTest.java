package com.alulalibre.app.aulalibre.roomrequest.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.alulalibre.app.aulalibre.availability.application.service.RoomAvailabilityService;
import com.alulalibre.app.aulalibre.room.application.service.RoomService;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.room.domain.repository.RoomRepository;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.request.CreateRoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.request.RejectRoomRequestRequest;
import com.alulalibre.app.aulalibre.roomrequest.application.dto.response.RoomRequestResponse;
import com.alulalibre.app.aulalibre.roomrequest.application.mapper.RoomRequestMapper;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.domain.repository.RoomRequestRepository;
import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ForbiddenOperationException;
import com.alulalibre.app.aulalibre.user.application.service.CurrentUserProvider;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomRequestServiceTest {

    @Mock
    private RoomRequestRepository roomRequestRepository;
    @Mock
    private RoomRequestMapper roomRequestMapper;
    @Mock
    private RoomService roomService;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomAvailabilityService availabilityService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private RoomRequestService roomRequestService;

    private User professor;
    private User admin;
    private Room room;

    @BeforeEach
    void setUp() {
        roomRequestService = new RoomRequestService(roomRequestRepository, roomRequestMapper, roomService,
                roomRepository, availabilityService, currentUserProvider);

        professor = new User();
        professor.setId(1L);
        professor.setRole(UserRole.PROFESSOR);

        admin = new User();
        admin.setId(2L);
        admin.setRole(UserRole.ADMIN);

        room = new Room();
        room.setId(10L);

        lenient().when(roomService.findEntityById(10L)).thenReturn(room);
        lenient().when(roomRequestMapper.toResponse(any(RoomRequest.class)))
                .thenReturn(new RoomRequestResponse(null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void create_rejectsWhenTheRoomIsAlreadyOccupied() {
        when(currentUserProvider.getCurrentUser()).thenReturn(professor);
        when(availabilityService.isRangeFree(room, LocalDate.of(2026, 9, 3), LocalTime.of(16, 0), LocalTime.of(18, 0)))
                .thenReturn(false);
        CreateRoomRequest request = new CreateRoomRequest(10L, LocalDate.of(2026, 9, 3),
                LocalTime.of(16, 0), LocalTime.of(18, 0), RoomRequestReason.TUTORING, "");

        assertThatThrownBy(() -> roomRequestService.create(request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_AVAILABLE);
    }

    // Role checks for create/approve/reject moved to @PreAuthorize on the controllers
    // (Security phase) — covered by SecurityAuthorizationIntegrationTest instead of here.

    @Test
    void approve_rejectsWhenTheRoomBecameUnavailableSinceTheRequestWasCreated() {
        RoomRequest pending = pendingRequest();
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        when(roomRequestRepository.findRoomIdById(100L)).thenReturn(Optional.of(10L));
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomRequestRepository.findById(100L)).thenReturn(Optional.of(pending));
        when(availabilityService.isRangeFree(room, pending.getDate(), pending.getStartTime(), pending.getEndTime()))
                .thenReturn(false);

        assertThatThrownBy(() -> roomRequestService.approve(100L))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_AVAILABLE);
        assertThat(pending.getStatus()).isEqualTo(RoomRequestStatus.PENDING);
    }

    @Test
    void approve_succeedsAndStampsTheReviewer() {
        RoomRequest pending = pendingRequest();
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        when(roomRequestRepository.findRoomIdById(100L)).thenReturn(Optional.of(10L));
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomRequestRepository.findById(100L)).thenReturn(Optional.of(pending));
        when(availabilityService.isRangeFree(room, pending.getDate(), pending.getStartTime(), pending.getEndTime()))
                .thenReturn(true);

        roomRequestService.approve(100L);

        assertThat(pending.getStatus()).isEqualTo(RoomRequestStatus.APPROVED);
        assertThat(pending.getReviewedBy()).isEqualTo(admin);
        assertThat(pending.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_rejectsWhenTheRequestIsNoLongerPending() {
        RoomRequest alreadyApproved = pendingRequest();
        alreadyApproved.setStatus(RoomRequestStatus.APPROVED);
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        when(roomRequestRepository.findRoomIdById(100L)).thenReturn(Optional.of(10L));
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomRequestRepository.findById(100L)).thenReturn(Optional.of(alreadyApproved));

        assertThatThrownBy(() -> roomRequestService.approve(100L))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST_STATE);
    }

    @Test
    void cancel_rejectsWhenTheCallerIsNotTheOwner() {
        RoomRequest pending = pendingRequest();
        User otherProfessor = new User();
        otherProfessor.setId(99L);
        otherProfessor.setRole(UserRole.PROFESSOR);
        when(currentUserProvider.getCurrentUser()).thenReturn(otherProfessor);
        when(roomRequestRepository.findById(100L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> roomRequestService.cancel(100L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void cancel_rejectsAnAlreadyApprovedRequest() {
        RoomRequest approved = pendingRequest();
        approved.setStatus(RoomRequestStatus.APPROVED);
        when(currentUserProvider.getCurrentUser()).thenReturn(professor);
        when(roomRequestRepository.findById(100L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> roomRequestService.cancel(100L))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST_STATE);
    }

    @Test
    void cancel_succeedsForTheOwnerOfAPendingRequest() {
        RoomRequest pending = pendingRequest();
        when(currentUserProvider.getCurrentUser()).thenReturn(professor);
        when(roomRequestRepository.findById(100L)).thenReturn(Optional.of(pending));

        roomRequestService.cancel(100L);

        assertThat(pending.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @Test
    void reject_setsTheReviewNote() {
        RoomRequest pending = pendingRequest();
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        when(roomRequestRepository.findById(100L)).thenReturn(Optional.of(pending));

        roomRequestService.reject(100L, new RejectRoomRequestRequest("El salón ya tenía una clase asignada"));

        assertThat(pending.getStatus()).isEqualTo(RoomRequestStatus.REJECTED);
        assertThat(pending.getReviewNote()).isEqualTo("El salón ya tenía una clase asignada");
    }

    private RoomRequest pendingRequest() {
        RoomRequest roomRequest = new RoomRequest();
        roomRequest.setId(100L);
        roomRequest.setProfessor(professor);
        roomRequest.setRoom(room);
        roomRequest.setDate(LocalDate.of(2026, 9, 3));
        roomRequest.setStartTime(LocalTime.of(16, 0));
        roomRequest.setEndTime(LocalTime.of(18, 0));
        roomRequest.setReason(RoomRequestReason.TUTORING);
        roomRequest.setStatus(RoomRequestStatus.PENDING);
        return roomRequest;
    }
}
