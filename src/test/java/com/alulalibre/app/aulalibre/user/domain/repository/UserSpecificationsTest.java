package com.alulalibre.app.aulalibre.user.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.alulalibre.app.aulalibre.shared.util.SpecificationUtil;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * Exercises the Specification/Pageable combination behind GET /users
 * (USER_MANAGEMENT_BACKEND_CONTRACT.md §6) against a real H2 database — the
 * concatenated full-name search in particular is Criteria API surface that
 * compiles fine but can break at query time.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSpecificationsTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void seed() {
        entityManager.persist(user("Ana", "Estudiante", "ana@aulalibre.edu", UserRole.STUDENT, true));
        entityManager.persist(user("Juan Carlos", "Pérez", "juan@aulalibre.edu", UserRole.PROFESSOR, true));
        entityManager.persist(user("Laura", "Administradora", "laura@aulalibre.edu", UserRole.ADMIN, false));
        entityManager.flush();
    }

    @Test
    void roleFilter_onlyReturnsMatchingUsers() {
        Specification<User> spec = SpecificationUtil.combine(UserSpecifications.role(UserRole.PROFESSOR));

        var page = userRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getEmail()).isEqualTo("juan@aulalibre.edu");
    }

    @Test
    void activeFilter_excludesInactiveUsers() {
        Specification<User> spec = SpecificationUtil.combine(UserSpecifications.active(false));

        var page = userRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getEmail()).isEqualTo("laura@aulalibre.edu");
    }

    @Test
    void searchByFullName_matchesAcrossFirstAndLastName() {
        Specification<User> spec = SpecificationUtil.combine(UserSpecifications.search("juan carlos"));

        var page = userRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getEmail()).isEqualTo("juan@aulalibre.edu");
    }

    @Test
    void searchByEmail_matchesCaseInsensitively() {
        Specification<User> spec = SpecificationUtil.combine(UserSpecifications.search("LAURA@AULALIBRE"));

        var page = userRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void noFilters_returnsEveryUser() {
        Specification<User> spec = SpecificationUtil.combine();

        var page = userRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    private User user(String firstName, String lastName, String email, UserRole role, boolean active) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(active);
        return user;
    }
}
