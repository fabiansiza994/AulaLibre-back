package com.alulalibre.app.aulalibre.shared.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Pagination envelope matching BACKEND_API_CONTRACT.md ({@code content, page,
 * size, totalElements, totalPages}), decoupled from Spring Data's own {@link Page}
 * JSON shape so the API contract doesn't change if the pagination library does.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
