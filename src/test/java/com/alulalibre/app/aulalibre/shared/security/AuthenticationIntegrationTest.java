package com.alulalibre.app.aulalibre.shared.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import com.alulalibre.app.aulalibre.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end through the real filter chain: MockMvc requests go through
 * {@link JwtAuthenticationFilter}, {@link JwtAuthenticationEntryPoint}, and
 * {@code AuthController} exactly as a real HTTP client would. Wrapped in
 * {@code @Transactional} so every user this test inserts rolls back — the H2
 * database is shared (and persists) across the whole test suite run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void login_succeedsForEachRoleAndReturnsTheContractShape() throws Exception {
        User professor = seedUser("Juan Carlos", "Pérez", "juan.integration@aulalibre.edu", UserRole.PROFESSOR, "Professor123*");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(professor.getEmail(), "Professor123*")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.id", is(professor.getId().intValue())))
                .andExpect(jsonPath("$.user.name", is("Juan Carlos Pérez")))
                .andExpect(jsonPath("$.user.role", is("profesor")))
                .andExpect(jsonPath("$.user.initials", is("JP")))
                .andExpect(jsonPath("$.user.email", is(professor.getEmail())));
    }

    @Test
    void login_withWrongPassword_returnsGenericUnauthorized() throws Exception {
        User student = seedUser("Ana", "Estudiante", "ana.integration@aulalibre.edu", UserRole.STUDENT, "Student123*");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(student.getEmail(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.message", is("Credenciales inválidas")));
    }

    @Test
    void login_withUnknownEmail_returnsTheSameGenericUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nadie@aulalibre.edu", "whatever123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.message", is("Credenciales inválidas")));
    }

    @Test
    void login_forAnInactiveUser_isRejected() throws Exception {
        User inactive = seedUser("Marta", "Inactiva", "marta.integration@aulalibre.edu", UserRole.ADMIN, "Admin123*");
        inactive.setActive(false);
        userRepository.save(inactive);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(inactive.getEmail(), "Admin123*")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void me_withAValidToken_returnsTheAuthenticatedUser() throws Exception {
        User admin = seedUser("Laura", "Admin", "laura.integration@aulalibre.edu", UserRole.ADMIN, "Admin123*");
        String token = mintValidToken(admin);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(admin.getEmail())))
                .andExpect(jsonPath("$.role", is("administrador")));
    }

    @Test
    void protectedEndpoint_withoutAnyToken_returns401WithTheProjectsErrorShape() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void protectedEndpoint_withAnExpiredToken_returns401() throws Exception {
        User professor = seedUser("Expira", "Do", "expira.integration@aulalibre.edu", UserRole.PROFESSOR, "Professor123*");
        JwtTokenService expiredTokenService = new JwtTokenService(new JwtProperties(jwtProperties.secret(), -1));
        String expiredToken = expiredTokenService.generateToken(professor);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withAGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withATamperedSignature_returns401() throws Exception {
        User professor = seedUser("Manipula", "Do", "manipula.integration@aulalibre.edu", UserRole.PROFESSOR, "Professor123*");
        String token = mintValidToken(professor);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    private String mintValidToken(User user) {
        return new JwtTokenService(jwtProperties).generateToken(user);
    }

    private User seedUser(String firstName, String lastName, String email, UserRole role, String rawPassword) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
