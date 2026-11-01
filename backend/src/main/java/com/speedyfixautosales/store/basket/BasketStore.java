package com.speedyfixautosales.store.basket;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Where baskets are kept: PostgreSQL with the "postgres" profile (JdbcBasketStore), otherwise memory. */
public interface BasketStore {

    /** A new, empty basket's id. */
    String create();

    /** Product id to quantity, in the order they were added; empty if there's no such basket. */
    Optional<Map<String, Integer>> items(String basketId);

    /** Sets a line's quantity; zero or less removes it. */
    void set(String basketId, String productId, int quantity);

    void clear(String basketId);

    /** Drops baskets nobody has changed since the cutoff. */
    void removeIdle(Instant cutoff);
}
