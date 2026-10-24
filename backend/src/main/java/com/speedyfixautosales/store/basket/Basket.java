package com.speedyfixautosales.store.basket;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A shopper's basket: product id to quantity, in the order items were added. */
public class Basket {

    private final String id = UUID.randomUUID().toString();
    private final Map<String, Integer> quantities = new LinkedHashMap<>();
    private Instant updatedAt = Instant.now();

    public String id() {
        return id;
    }

    public synchronized Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(quantities);
    }

    synchronized int quantityOf(String productId) {
        return quantities.getOrDefault(productId, 0);
    }

    synchronized void set(String productId, int quantity) {
        if (quantity <= 0) {
            quantities.remove(productId);
        } else {
            quantities.put(productId, quantity);
        }
        updatedAt = Instant.now();
    }

    public synchronized Instant updatedAt() {
        return updatedAt;
    }
}
