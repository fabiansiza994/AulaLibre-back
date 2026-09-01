package com.alulalibre.app.aulalibre.room.domain.repository;

import com.alulalibre.app.aulalibre.room.domain.model.Room;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByBlockId(Long blockId);

    long countByBlockId(Long blockId);

    /**
     * Pessimistic write lock used by RoomRequestService#approve so two
     * concurrent approvals for the same room serialize on this row instead
     * of both reading a "still available" snapshot and double-booking it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(Long id);
}
