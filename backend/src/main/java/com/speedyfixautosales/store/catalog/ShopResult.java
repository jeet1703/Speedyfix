package com.speedyfixautosales.store.catalog;

import java.util.List;

/**
 * One page of shop results, plus every category with how many parts it holds, and the values shoppers can filter
 * by (Brand, Sub type, Featured refinements). Each filter's counts respect every other filter but its own, as on eBay.
 */
public record ShopResult(
        List<ProductView> items,
        int total,
        int page,
        int pageSize,
        int pageCount,
        List<CategoryCount> categories,
        List<Facet> brands,
        List<Facet> subTypes,
        List<Facet> refinements) {

    public record CategoryCount(String id, String name, long count) {
    }

    public record Facet(String value, long count) {
    }
}
