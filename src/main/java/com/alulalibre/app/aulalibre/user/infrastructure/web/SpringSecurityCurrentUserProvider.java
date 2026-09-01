package com.alulalibre.app.aulalibre.user.infrastructure.web;

import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ForbiddenOperationException;
import com.alulalibre.app.aulalibre.shared.exception.ResourceNotFoundException;
import com.alulalibre.app.aulalibre.user.application.service.CurrentUserProvider;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import com.alulalibre.app.aulalibre.user.domain.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Production {@link CurrentUserProvider}: reads the user id
 * {@code JwtAuthenticationFilter} placed as the {@code Authentication}
 * principal and reloads the {@link User} from the database (never trusts a
 * cached/stale copy). Every endpoint that reaches this is already behind
 * {@code @PreAuthorize}/{@code authenticated()}, so a missing Authentication
 * here would indicate a wiring bug rather than a normal request — handled
 * defensively rather than assumed impossible.
 *
 * <p>Depends on {@link UserRepository} directly rather than {@code UserService}
 * on purpose: {@code UserService} (account management) needs a
 * {@link CurrentUserProvider} of its own for the "can't disable/demote
 * yourself" checks, and going through the service here would create a
 * constructor-injection cycle (UserService → CurrentUserProvider →
 * UserService) that Spring can't resolve.
 */
@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    private final UserRepository userRepository;

    public SpringSecurityCurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN_OPERATION,
                    "No hay un usuario autenticado en el contexto actual");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                        "El usuario solicitado no existe"));
    }
}
