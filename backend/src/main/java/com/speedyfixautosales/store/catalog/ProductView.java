package com.speedyfixautosales.store.catalog;

import java.math.BigDecimal;

/**
 * A product as the storefront shows it, including its fit for the shopper's vehicle. price is what the shopper pays;
 * during a sale, originalPrice is the price before it (shown struck through) and salePercent the discount.
 */
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
        FitStatus fit,
        BigDecimal originalPrice,
        Integer salePercent) {
}
