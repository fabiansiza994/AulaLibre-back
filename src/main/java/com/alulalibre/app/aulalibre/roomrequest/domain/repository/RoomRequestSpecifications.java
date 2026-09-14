package com.alulalibre.app.aulalibre.roomrequest.domain.repository;

import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Combinable filters for {@code GET /room-requests/my} (BACKEND_API_CONTRACT.md
 * §7.1: page/size/status/search/dateFrom/dateTo/roomId/reason) and the admin
 * listing (§7.5/7.6: status/dateFrom). Kept as small, independently
 * combinable predicates instead of one branching query method.
 */
public final class RoomRequestSpecifications {

    private RoomRequestSpecifications() {
    }

    public static Specification<RoomRequest> professorId(Long professorId) {
        return (root, query, cb) -> cb.equal(root.get("professor").get("id"), professorId);
    }

    public static Specification<RoomRequest> status(RoomRequestStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<RoomRequest> roomId(Long roomId) {
        return roomId == null ? null : (root, query, cb) -> cb.equal(root.get("room").get("id"), roomId);
    }

    public static Specification<RoomRequest> reason(RoomRequestReason reason) {
        return reason == null ? null : (root, query, cb) -> cb.equal(root.get("reason"), reason);
    }

    public static Specification<RoomRequest> dateFrom(LocalDate dateFrom) {
        return dateFrom == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), dateFrom);
    }

    public static Specification<RoomRequest> dateTo(LocalDate dateTo) {
        return dateTo == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), dateTo);
    }

    /**
     * Matches room name, note or the reason's Spanish label, case-insensitive.
     * {@code reason} is persisted as its English constant name, so matching it
     * against the search text is done in Java (against the small, fixed set
     * of labels) rather than as a SQL LIKE on the raw column value.
     */
    public static Specification<RoomRequest> search(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String needle = search.toLowerCase();
        String pattern = "%" + needle + "%";
        List<RoomRequestReason> matchingReasons = Arrays.stream(RoomRequestReason.values())
                .filter(reason -> reason.getLabel().toLowerCase().contains(needle))
                .toList();
        return (root, query, cb) -> {
            Join<RoomRequest, Room> room = root.join("room");
            Predicate byRoomName = cb.like(cb.lower(room.get("name")), pattern);
            Predicate byNote = cb.like(cb.lower(cb.coalesce(root.get("note"), "")), pattern);
            Predicate combined = cb.or(byRoomName, byNote);
            if (!matchingReasons.isEmpty()) {
                combined = cb.or(combined, root.get("reason").in(matchingReasons));
            }
            return combined;
        };
    }
}
