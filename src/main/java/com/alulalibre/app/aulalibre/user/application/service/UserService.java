package com.alulalibre.app.aulalibre.user.application.service;

import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ResourceNotFoundException;
import com.alulalibre.app.aulalibre.shared.response.PageResponse;
import com.alulalibre.app.aulalibre.shared.util.SpecificationUtil;
import com.alulalibre.app.aulalibre.user.application.dto.request.CreateUserRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserPasswordRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserRequest;
import com.alulalibre.app.aulalibre.user.application.dto.request.UpdateUserStatusRequest;
import com.alulalibre.app.aulalibre.user.application.dto.response.UserResponse;
import com.alulalibre.app.aulalibre.user.application.mapper.UserMapper;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import com.alulalibre.app.aulalibre.user.domain.repository.UserRepository;
import com.alulalibre.app.aulalibre.user.domain.repository.UserSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account management for admins (USER_MANAGEMENT_BACKEND_CONTRACT.md) — not
 * the authenticated session itself (see {@code AuthService}/{@code AuthUserResponse}).
 * Every public method here is reachable only via {@code UserController}'s
 * class-level {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder,
            CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                        "El usuario solicitado no existe"));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Pageable pageable, String search, UserRole role, Boolean active) {
        Specification<User> spec = SpecificationUtil.combine(
                UserSpecifications.search(search),
                UserSpecifications.role(role),
                UserSpecifications.active(active));
        return PageResponse.of(userRepository.findAll(spec, pageable).map(userMapper::toResponse));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim();
        requireEmailNotTaken(email, null);

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(email);
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findEntityById(id);
        String email = request.email().trim();
        requireEmailNotTaken(email, id);
        requireNotChangingOwnRole(id, request.role());

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(email);
        user.setRole(request.role());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        User user = findEntityById(id);
        if (!request.active() && currentUserProvider.getCurrentUser().getId().equals(id)) {
            throw new ConflictException(ErrorCode.CANNOT_DISABLE_SELF,
                    "No podés desactivar tu propia cuenta");
        }
        user.setActive(request.active());
        return userMapper.toResponse(user);
    }

    @Transactional
    public void updatePassword(Long id, UpdateUserPasswordRequest request) {
        User user = findEntityById(id);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
    }

    private void requireEmailNotTaken(String email, Long excludingUserId) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> excludingUserId == null || !existing.getId().equals(excludingUserId))
                .ifPresent(existing -> {
                    throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS,
                            "Ya existe un usuario con el email '%s'".formatted(email));
                });
    }

    private void requireNotChangingOwnRole(Long targetUserId, UserRole newRole) {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getId().equals(targetUserId) && newRole != UserRole.ADMIN) {
            throw new ConflictException(ErrorCode.CANNOT_CHANGE_OWN_ROLE,
                    "No podés cambiar tu propio rol de administrador");
        }
    }
}
