package com.speedyfixautosales.store.basket;

import java.math.BigDecimal;
import java.util.List;

/** Basket totals as the storefront shows them. All amounts in GBP, VAT included. */
public record BasketView(
        String id,
        List<Line> lines,
        int itemCount,
        BigDecimal subtotal,
        Delivery delivery,
        BigDecimal total,
        BigDecimal vatIncluded) {

    public record Line(String productId, String name, String reference, String imageUrl, BigDecimal unitPrice,
                       int quantity, BigDecimal lineTotal, int stock) {
    }

    /** method is PALLET, STANDARD or NONE (empty basket). */
    public record Delivery(String method, String label, BigDecimal price) {
    }
}
