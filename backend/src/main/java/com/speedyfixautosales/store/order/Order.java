package com.speedyfixautosales.store.order;

import com.speedyfixautosales.store.basket.BasketView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A placed order. The reference is random so it can't be guessed from another order's.
 * Payment isn't connected yet, so every order starts as AWAITING_PAYMENT.
 */
public record Order(
        String reference,
        Instant placedAt,
        Status status,
        Customer customer,
        Address address,
        String notes,
        List<BasketView.Line> lines,
        BasketView.Delivery delivery,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal vatIncluded) {

    public enum Status {
        AWAITING_PAYMENT
    }

    public record Customer(String name, String email, String phone) {
    }

    public record Address(String line1, String line2, String town, String postcode) {
    }
}
