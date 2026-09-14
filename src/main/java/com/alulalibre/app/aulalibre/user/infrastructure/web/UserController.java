package com.alulalibre.app.aulalibre.user.infrastructure.web;

import com.alulalibre.app.aulalibre.shared.response.PageResponse;
import com.alulalibre.app.aulalibre.user.application.dto.request.CreateUserRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserPasswordRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserStatusRequest;
import com.alulalibre.app.aulalibre.user.application.dto.response.UserResponse;
import com.alulalibre.app.aulalibre.user.application.service.UserService;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * USER_MANAGEMENT_BACKEND_CONTRACT.md — account administration (create,
 * edit, activate/deactivate, admin password reset). "Admin only" applies to
 * every endpoint including the GETs (§2), so the class-level
 * {@code @PreAuthorize} covers all of them instead of repeating it per method
 * like blocks/rooms/schedules do (those allow reads to any authenticated role).
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserResponse> findAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active) {
        return userService.findAll(pageable, search, role, active);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.updateStatus(id, request);
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(@PathVariable Long id, @Valid @RequestBody UpdateUserPasswordRequest request) {
        userService.updatePassword(id, request);
    }
}
