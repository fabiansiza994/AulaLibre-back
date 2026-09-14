package com.alulalibre.app.aulalibre.shared.util;

import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/**
 * {@code Specification.allOf(...)} does NOT skip null elements in the
 * Spring Data JPA version this project is on — it reduces with
 * {@code unrestricted().and(...)} over every element as-is, so a single null
 * specification (any optional filter a caller left unset) throws
 * {@code IllegalArgumentException: Other specification must not be null}.
 * Every module with optional query-param filters (room requests, users, ...)
 * must go through this instead of calling {@code Specification.allOf(...)}
 * directly.
 */
public final class SpecificationUtil {

    private SpecificationUtil() {
    }

    @SafeVarargs
    public static <T> Specification<T> combine(Specification<T>... specs) {
        return Specification.allOf(Stream.of(specs).filter(Objects::nonNull).toList());
    }
}
