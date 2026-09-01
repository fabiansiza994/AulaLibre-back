package com.alulalibre.app.aulalibre.roomrequest.domain.model;

import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.shared.domain.BaseEntity;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A professor's temporary request to use a room outside their regular
 * academic schedule. Once APPROVED, this range must also be treated as an
 * occupation block when computing room availability.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "room_requests")
public class RoomRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private User professor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoomRequestReason reason;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomRequestStatus status = RoomRequestStatus.PENDING;

    /** Not exposed in RoomRequestResponse (not part of the contract) — kept for audit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    /** Not exposed in RoomRequestResponse (not part of the contract) — kept for audit. */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;
}
