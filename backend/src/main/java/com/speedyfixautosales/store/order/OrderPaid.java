package com.speedyfixautosales.store.order;

/** Published when an order is paid: PayPal confirmed it, or staff marked it paid. The order is already saved. */
public record OrderPaid(Order order) {
}
