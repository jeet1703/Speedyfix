package com.speedyfix.store.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Filters for the shop page. Every field is optional; page is 1-based. */
public record ShopQuery(
        String category,
        String q,
        Set<Condition> conditions,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean fitsOnly,
        String vehicleId,
        Sort sort,
        int page,
        int pageSize,
        List<String> ids) {

    public enum Sort {
        BEST_SELLING, NEWEST, PRICE_LOW, PRICE_HIGH
    }

    public ShopQuery {
        conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
        sort = sort == null ? Sort.BEST_SELLING : sort;
        page = Math.max(1, page);
        pageSize = Math.max(1, Math.min(pageSize, 48));
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
