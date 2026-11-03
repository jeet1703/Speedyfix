package com.speedyfixautosales.store.order;

/** Published when an order is cancelled: left unpaid too long, or cancelled by staff. The order is already saved. */
public record OrderCancelled(Order order) {
}
