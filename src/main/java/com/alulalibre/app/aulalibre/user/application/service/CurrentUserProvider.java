package com.alulalibre.app.aulalibre.user.application.service;

import com.alulalibre.app.aulalibre.user.domain.model.User;

/**
 * Resolves "who is calling right now". Services depend only on this
 * interface, never on how the caller was identified — today that's
 * {@code DevCurrentUserProvider} (a temporary, pre-auth bridge); once JWT/
 * Spring Security exists, a {@code SpringSecurityCurrentUserProvider} reads
 * it from the SecurityContext instead, and no service changes.
 */
public interface CurrentUserProvider {

    User getCurrentUser();
}
