package com.amandadamata.ticketing.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** A stable JSON shape for pages, instead of serializing Spring's {@code Page} directly. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
