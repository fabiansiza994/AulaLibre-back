package com.alulalibre.app.aulalibre.roomrequest.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.alulalibre.app.aulalibre.block.domain.model.Block;
import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Exercises the Specification/Pageable combination behind GET
 * /room-requests/my (BACKEND_API_CONTRACT.md §7.1) against a real H2
 * database — Criteria API joins/enum comparisons are exactly the kind of
 * thing that compiles fine but breaks at query time.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomRequestSpecificationsTest {

    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User professor;
    private Room room201;
    private Room room202;

    @BeforeEach
    void seed() {
        Block block = new Block();
        block.setCode("B1");
        block.setName("Bloque 1");
        entityManager.persist(block);

        room201 = new Room();
        room201.setName("Salón 201");
        room201.setBlock(block);
        room201.setFloor(2);
        room201.setCapacity(30);
        room201.setType(RoomType.CLASSROOM);
        entityManager.persist(room201);

        room202 = new Room();
        room202.setName("Salón 202");
        room202.setBlock(block);
        room202.setFloor(2);
        room202.setCapacity(20);
        room202.setType(RoomType.CLASSROOM);
        entityManager.persist(room202);

        professor = new User();
        professor.setFirstName("Ana");
        professor.setLastName("Profe");
        professor.setEmail("ana@aulalibre.edu");
        professor.setRole(UserRole.PROFESSOR);
        entityManager.persist(professor);

        entityManager.persist(request(room201, LocalDate.of(2026, 9, 1), RoomRequestStatus.PENDING, RoomRequestReason.TUTORING));
        entityManager.persist(request(room201, LocalDate.of(2026, 9, 2), RoomRequestStatus.APPROVED, RoomRequestReason.ACADEMIC_MEETING));
        entityManager.persist(request(room202, LocalDate.of(2026, 9, 5), RoomRequestStatus.REJECTED, RoomRequestReason.OTHER));
        entityManager.flush();
    }

    @Test
    void statusFilter_onlyReturnsMatchingRequests() {
        Specification<RoomRequest> spec = Specification.<RoomRequest>allOf()
                .and(RoomRequestSpecifications.professorId(professor.getId()))
                .and(RoomRequestSpecifications.status(RoomRequestStatus.PENDING));

        var page = roomRequestRepository.findAll(spec, PageRequest.of(0, 10, Sort.by("date")));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(RoomRequestStatus.PENDING);
    }

    @Test
    void roomIdFilter_narrowsToThatRoomOnly() {
        Specification<RoomRequest> spec = Specification.<RoomRequest>allOf()
                .and(RoomRequestSpecifications.professorId(professor.getId()))
                .and(RoomRequestSpecifications.roomId(room202.getId()));

        var page = roomRequestRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getRoom().getId()).isEqualTo(room202.getId());
    }

    @Test
    void dateRangeFilter_excludesRequestsOutsideTheWindow() {
        Specification<RoomRequest> spec = Specification.<RoomRequest>allOf()
                .and(RoomRequestSpecifications.professorId(professor.getId()))
                .and(RoomRequestSpecifications.dateFrom(LocalDate.of(2026, 9, 1)))
                .and(RoomRequestSpecifications.dateTo(LocalDate.of(2026, 9, 2)));

        var page = roomRequestRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void searchByRoomName_matchesCaseInsensitively() {
        Specification<RoomRequest> spec = Specification.<RoomRequest>allOf()
                .and(RoomRequestSpecifications.professorId(professor.getId()))
                .and(RoomRequestSpecifications.search("202"));

        var page = roomRequestRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getRoom().getName()).isEqualTo("Salón 202");
    }

    @Test
    void searchByReasonLabel_matchesTheSpanishLabelNotTheEnumConstant() {
        Specification<RoomRequest> spec = Specification.<RoomRequest>allOf()
                .and(RoomRequestSpecifications.professorId(professor.getId()))
                .and(RoomRequestSpecifications.search("tutor"));

        var page = roomRequestRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getReason()).isEqualTo(RoomRequestReason.TUTORING);
    }

    @Test
    void pagination_splitsResultsAcrossPages() {
        Specification<RoomRequest> spec = RoomRequestSpecifications.professorId(professor.getId());

        var firstPage = roomRequestRepository.findAll(spec, PageRequest.of(0, 2, Sort.by("date")));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    private RoomRequest request(Room room, LocalDate date, RoomRequestStatus status, RoomRequestReason reason) {
        RoomRequest roomRequest = new RoomRequest();
        roomRequest.setProfessor(professor);
        roomRequest.setRoom(room);
        roomRequest.setDate(date);
        roomRequest.setStartTime(LocalTime.of(10, 0));
        roomRequest.setEndTime(LocalTime.of(11, 0));
        roomRequest.setReason(reason);
        roomRequest.setStatus(status);
        return roomRequest;
    }
}
