package com.alulalibre.app.aulalibre.auth.infrastructure.web;

import com.alulalibre.app.aulalibre.auth.application.dto.request.LoginRequest;
import com.alulalibre.app.aulalibre.auth.application.dto.response.AuthResponse;
import com.alulalibre.app.aulalibre.auth.application.dto.response.AuthUserResponse;
import com.alulalibre.app.aulalibre.auth.application.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /login} is public (see SecurityConfig); {@code /me} and
 * {@code /logout} require only {@code authenticated()} — any role — which is
 * already the default for every endpoint SecurityConfig doesn't explicitly
 * list as public, so neither needs its own {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.me();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Stateless JWT: nothing to invalidate server-side. The client discards
        // the token; a token already issued stays valid until it expires
        // naturally (see SECURITY_IMPLEMENTATION_PLAN.md §7 — no blacklist).
    }
}
