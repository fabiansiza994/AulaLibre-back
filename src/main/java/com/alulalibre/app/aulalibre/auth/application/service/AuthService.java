package com.alulalibre.app.aulalibre.auth.application.service;

import com.alulalibre.app.aulalibre.auth.application.dto.request.LoginRequest;
import com.alulalibre.app.aulalibre.auth.application.dto.response.AuthResponse;
import com.alulalibre.app.aulalibre.auth.application.dto.response.AuthUserResponse;
import com.alulalibre.app.aulalibre.shared.exception.AuthenticationFailedException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.security.JwtTokenService;
import com.alulalibre.app.aulalibre.user.application.service.CurrentUserProvider;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import com.alulalibre.app.aulalibre.user.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /**
     * A real BCrypt hash of a random, never-used string. Checked when no
     * user matches the email so a lookup miss costs roughly the same time as
     * a real password check — without this, "unknown email" would return
     * near-instantly while "wrong password" always pays the BCrypt cost,
     * letting a client infer which emails exist from response time alone.
     */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO9wOJ4SzWWXR5wKvXn8DrUnLZ8lqOx8O";

    private static final String GENERIC_LOGIN_FAILURE_MESSAGE = "Credenciales inválidas";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final CurrentUserProvider currentUserProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService, CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        String hashToCheck = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        // Never differentiate "email doesn't exist" / "wrong password" / "inactive" in the response.
        if (user == null || !user.isActive() || !passwordMatches) {
            throw new AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, GENERIC_LOGIN_FAILURE_MESSAGE);
        }

        String token = jwtTokenService.generateToken(user);
        return new AuthResponse(token, toAuthUserResponse(user));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me() {
        return toAuthUserResponse(currentUserProvider.getCurrentUser());
    }

    private AuthUserResponse toAuthUserResponse(User user) {
        String name = user.getFirstName() + " " + user.getLastName();
        String initials = "" + Character.toUpperCase(user.getFirstName().charAt(0))
                + Character.toUpperCase(user.getLastName().charAt(0));
        return new AuthUserResponse(user.getId(), name, user.getRole(), initials, user.getEmail());
    }
}
