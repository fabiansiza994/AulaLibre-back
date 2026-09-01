package com.alulalibre.app.aulalibre.user.domain.repository;

import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import org.springframework.data.jpa.domain.Specification;

/**
 * Combinable filters for {@code GET /users}
 * (USER_MANAGEMENT_BACKEND_CONTRACT.md §6: search/role/active).
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Matches first name, last name, "first last" full name, or email — case-insensitive. */
    public static Specification<User> search(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern),
                cb.like(cb.lower(root.get("email")), pattern));
    }

    public static Specification<User> role(UserRole role) {
        return role == null ? null : (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> active(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
