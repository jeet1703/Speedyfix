package com.speedyfixautosales.store.catalog;

import java.math.BigDecimal;

/** A product as the storefront shows it, including its fit for the shopper's vehicle. */
public record ProductView(
        String id,
        String name,
        String categoryId,
        String categoryName,
        String condition,
        String grade,
        BigDecimal price,
        String reference,
        String imageUrl,
        Shipping shipping,
        int stock,
        boolean inStock,
        FitStatus fit) {
}
