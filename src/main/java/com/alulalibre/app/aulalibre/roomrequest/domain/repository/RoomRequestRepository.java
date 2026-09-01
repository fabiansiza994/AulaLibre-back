package com.alulalibre.app.aulalibre.roomrequest.domain.repository;

import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRequestRepository extends JpaRepository<RoomRequest, Long>,
        JpaSpecificationExecutor<RoomRequest> {

    boolean existsByRoomId(Long roomId);

    /** Approved occupation for a room/date — the input to availability calculations. */
    List<RoomRequest> findByRoomIdAndDateAndStatus(Long roomId, LocalDate date, RoomRequestStatus status);

    /**
     * Scalar projection (not an entity fetch) so RoomRequestService#approve
     * can learn which Room to lock without pulling a stale RoomRequest into
     * the persistence context before the lock is acquired.
     */
    @Query("select r.room.id from RoomRequest r where r.id = :id")
    Optional<Long> findRoomIdById(@Param("id") Long id);
}
