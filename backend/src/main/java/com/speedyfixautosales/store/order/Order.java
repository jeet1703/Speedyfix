package com.speedyfixautosales.store.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.speedyfixautosales.store.basket.BasketView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A placed order. The reference is random so it can't be guessed from another order's.
 * Payment isn't connected yet, so every order starts as AWAITING_PAYMENT; staff mark it paid in the admin
 * (AWAITING_DISPATCH), then dispatched with the courier and tracking number. Cancelling puts the stock back.
 * customerId is the shopper's account when they were signed in at checkout (null for a guest); it isn't sent out.
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
        BigDecimal vatIncluded,
        Dispatch dispatch,
        Payment payment,
        @JsonIgnore String customerId) {

    public enum Status {
        AWAITING_PAYMENT, AWAITING_DISPATCH, DISPATCHED, CANCELLED
    }

    public record Customer(String name, String email, String phone) {
    }

    public record Address(String line1, String line2, String town, String postcode) {
    }

    /** How and when the order left. Null until it's dispatched. */
    public record Dispatch(String courier, String trackingNumber, Instant dispatchedAt) {
    }

    /**
     * An online payment through PayPal. Null until the shopper starts paying; captureId and paidAt stay null until
     * PayPal has taken the money. Orders staff mark as paid by hand (phone, bank transfer) have no payment.
     */
    public record Payment(String paypalOrderId, String captureId, Instant paidAt) {
    }

    public Order withStatus(Status newStatus, Dispatch newDispatch) {
        return new Order(reference, placedAt, newStatus, customer, address, notes, lines, delivery, subtotal, total,
                vatIncluded, newDispatch, payment, customerId);
    }

    public Order withPayment(Payment newPayment) {
        return new Order(reference, placedAt, status, customer, address, notes, lines, delivery, subtotal, total,
                vatIncluded, dispatch, newPayment, customerId);
    }

    public int itemCount() {
        return lines.stream().mapToInt(BasketView.Line::quantity).sum();
    }
}
