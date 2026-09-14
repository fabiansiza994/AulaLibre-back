package com.alulalibre.app.aulalibre.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alulalibre.app.aulalibre.block.domain.model.Block;
import com.alulalibre.app.aulalibre.block.domain.repository.BlockRepository;
import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import com.alulalibre.app.aulalibre.room.domain.model.Room;
import com.alulalibre.app.aulalibre.room.domain.repository.RoomRepository;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.roomrequest.domain.model.RoomRequest;
import com.alulalibre.app.aulalibre.roomrequest.domain.repository.RoomRequestRepository;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import com.alulalibre.app.aulalibre.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role-based authorization (via {@code @PreAuthorize}) and ownership (kept
 * in {@code RoomRequestService}, not expressible as a simple role check) —
 * both exercised through real HTTP requests so a regression in either layer
 * shows up here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JwtProperties jwtProperties;

    private User student;
    private User professorA;
    private User professorB;
    private User admin;
    private Room room;

    @BeforeEach
    void seed() {
        student = seedUser("Est", "Udiante", "authz.student@aulalibre.edu", UserRole.STUDENT);
        professorA = seedUser("Profe", "A", "authz.profa@aulalibre.edu", UserRole.PROFESSOR);
        professorB = seedUser("Profe", "B", "authz.profb@aulalibre.edu", UserRole.PROFESSOR);
        admin = seedUser("Adm", "In", "authz.admin@aulalibre.edu", UserRole.ADMIN);

        Block block = new Block();
        block.setCode("AUTHZ");
        block.setName("Bloque Authz");
        blockRepository.save(block);

        room = new Room();
        room.setName("Salón Authz");
        room.setBlock(block);
        room.setFloor(1);
        room.setCapacity(10);
        room.setType(RoomType.CLASSROOM);
        roomRepository.save(room);
    }

    @Test
    void student_canReadRooms() throws Exception {
        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearer(student)))
                .andExpect(status().isOk());
    }

    @Test
    void student_cannotCreateABlock() throws Exception {
        mockMvc.perform(post("/api/v1/blocks")
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X1\",\"name\":\"X\",\"description\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void professor_canCreateARoomRequest() throws Exception {
        String body = """
                {"roomId": %d, "date": "%s", "start": "16:00", "end": "18:00", "reason": "Tutoría", "note": ""}
                """.formatted(room.getId(), LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/room-requests")
                        .header("Authorization", bearer(professorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void professor_cannotApproveARoomRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/room-requests/999999/approve").header("Authorization", bearer(professorA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canApproveAPendingRoomRequest() throws Exception {
        RoomRequest pending = pendingRequest(professorA);

        mockMvc.perform(patch("/api/v1/room-requests/" + pending.getId() + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void professorA_cannotCancelProfessorBsRequest() throws Exception {
        RoomRequest ownedByB = pendingRequest(professorB);

        mockMvc.perform(patch("/api/v1/room-requests/" + ownedByB.getId() + "/cancel")
                        .header("Authorization", bearer(professorA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void professor_cannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(professorA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_cannotDisableTheirOwnAccount() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + admin.getId() + "/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    @Test
    void admin_canDisableAnotherUsersAccount() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + professorA.getId() + "/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void admin_cannotChangeTheirOwnRole() throws Exception {
        String body = """
                {"firstName": "Adm", "lastName": "In", "email": "%s", "role": "profesor"}
                """.formatted(admin.getEmail());

        mockMvc.perform(put("/api/v1/users/" + admin.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));
    }

    private RoomRequest pendingRequest(User professor) {
        RoomRequest roomRequest = new RoomRequest();
        roomRequest.setProfessor(professor);
        roomRequest.setRoom(room);
        roomRequest.setDate(LocalDate.now().plusDays(1));
        roomRequest.setStartTime(LocalTime.of(10, 0));
        roomRequest.setEndTime(LocalTime.of(11, 0));
        roomRequest.setReason(RoomRequestReason.TUTORING);
        roomRequest.setStatus(RoomRequestStatus.PENDING);
        return roomRequestRepository.save(roomRequest);
    }

    private String bearer(User user) {
        return "Bearer " + new JwtTokenService(jwtProperties).generateToken(user);
    }

    private User seedUser(String firstName, String lastName, String email, UserRole role) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoO9wOJ4SzWWXR5wKvXn8DrUnLZ8lqOx8O");
        return userRepository.save(user);
    }
}
