package com.accsaber.backend.service.market;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import com.accsaber.backend.model.dto.request.market.MarketSortOption;

final class MarketSort {

    private static final String PRICE_EXPRESSION = "COALESCE(l.currentBid, l.startingBid, l.buyoutPrice)";

    private MarketSort() {
    }

    static Pageable apply(MarketSortOption option, Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolve(option));
    }

    private static Sort resolve(MarketSortOption option) {
        if (option == null) {
            return Sort.by(Sort.Direction.ASC, "endsAt");
        }
        return switch (option) {
            case ending_soon -> Sort.by(Sort.Direction.ASC, "endsAt");
            case newest -> Sort.by(Sort.Direction.DESC, "createdAt");
            case price_asc -> JpaSort.unsafe(Sort.Direction.ASC, PRICE_EXPRESSION);
            case price_desc -> JpaSort.unsafe(Sort.Direction.DESC, PRICE_EXPRESSION);
        };
    }
}
