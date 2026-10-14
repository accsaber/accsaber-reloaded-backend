package com.accsaber.backend.util;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

public final class SortAliases {

    private SortAliases() {
    }

    public static Pageable translate(Pageable pageable, Map<String, String> aliases, boolean nullsLast) {
        for (Sort.Order order : pageable.getSort()) {
            String expression = aliases.get(order.getProperty());
            if (expression != null) {
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        unsafe(order.getDirection(), expression, nullsLast));
            }
        }
        return pageable;
    }

    public static Pageable translateEach(Pageable pageable, Map<String, String> aliases) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort resolved = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String expression = aliases.get(order.getProperty());
            if (expression != null) {
                resolved = resolved.and(unsafe(order.getDirection(), expression, true));
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
    }

    public static Sort nullsLast(Sort sort) {
        return Sort.by(sort.stream().map(order -> order.with(Sort.NullHandling.NULLS_LAST)).toList());
    }

    private static Sort unsafe(Sort.Direction direction, String expression, boolean nullsLast) {
        JpaSort sort = JpaSort.unsafe(direction, expression);
        return nullsLast ? nullsLast(sort) : sort;
    }
}
