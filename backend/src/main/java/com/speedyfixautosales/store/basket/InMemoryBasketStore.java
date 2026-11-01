package com.speedyfixautosales.store.basket;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Baskets in memory, for running without a database (a restart clears them). */
@Component
@Profile("!postgres")
public class InMemoryBasketStore implements BasketStore {

    private final Map<String, Basket> baskets = new ConcurrentHashMap<>();

    @Override
    public String create() {
        Basket basket = new Basket();
        baskets.put(basket.id(), basket);
        return basket.id();
    }

    @Override
    public Optional<Map<String, Integer>> items(String basketId) {
        return Optional.ofNullable(basketId == null ? null : baskets.get(basketId)).map(Basket::snapshot);
    }

    @Override
    public void set(String basketId, String productId, int quantity) {
        Basket basket = baskets.get(basketId);
        if (basket != null) {
            basket.set(productId, quantity);
        }
    }

    @Override
    public void clear(String basketId) {
        Basket basket = baskets.get(basketId);
        if (basket != null) {
            basket.snapshot().keySet().forEach(productId -> basket.set(productId, 0));
        }
    }

    @Override
    public void removeIdle(Instant cutoff) {
        baskets.values().removeIf(b -> b.updatedAt().isBefore(cutoff));
    }
}
