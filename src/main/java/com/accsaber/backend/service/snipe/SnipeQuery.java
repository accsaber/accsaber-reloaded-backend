package com.accsaber.backend.service.snipe;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;
import com.accsaber.backend.repository.SortAliases;

public record SnipeQuery(Long sniperId, Long targetId, String categoryCode, SnipeSort sort, Sort.Direction direction,
        SnipeUnplayed unplayed) {

    public SnipeQuery {
        sort = sort == null ? SnipeSort.GAP : sort;
        direction = direction != null ? direction
                : sort.isDescendingByDefault() ? Sort.Direction.DESC : Sort.Direction.ASC;
        unplayed = unplayed == null ? SnipeUnplayed.EXCLUDE : unplayed;
    }

    public boolean isDefaultOrder() {
        return sort == SnipeSort.GAP && direction.isAscending();
    }

    public String orderSlug() {
        return sort.getSlug() + "-" + direction.name().toLowerCase();
    }

    public String orderLabel() {
        return sort.getLabel() + (direction.isDescending() ? " high to low" : " low to high");
    }

    public Sort toSort() {
        return SortAliases.nullsLast(JpaSort.unsafe(direction, sort.getExpression()))
                .and(JpaSort.unsafe(Sort.Direction.ASC, "s_b.id"));
    }
}
