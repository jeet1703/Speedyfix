package com.speedyfix.store.catalog;

import java.util.List;

/** One page of shop results, plus every category with how many parts it holds. */
public record ShopResult(
        List<ProductView> items,
        int total,
        int page,
        int pageSize,
        int pageCount,
        List<CategoryCount> categories) {

    public record CategoryCount(String id, String name, long count) {
    }
}
