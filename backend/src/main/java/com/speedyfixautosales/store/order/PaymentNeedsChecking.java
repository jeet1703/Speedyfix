package com.speedyfixautosales.store.order;

/**
 * Published when PayPal reports a payment for the order that doesn't match it (status, currency, amount or reference).
 * Money may have moved, so staff must look in PayPal. paypalStatus, currency and amount are what PayPal said.
 */
public record PaymentNeedsChecking(Order order, String paypalStatus, String currency, java.math.BigDecimal amount) {
}
