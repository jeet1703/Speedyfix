package com.speedyfixautosales.store.basket;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Baskets in PostgreSQL (baskets, basket_items), so a shopper's basket survives a restart of the server. */
@Component
@Profile("postgres")
public class JdbcBasketStore implements BasketStore {

    private final JdbcTemplate jdbc;

    public JdbcBasketStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String create() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO baskets (id) VALUES (?)", id);
        return id;
    }

    @Override
    public Optional<Map<String, Integer>> items(String basketId) {
        if (basketId == null || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM baskets WHERE id = ?)", Boolean.class, basketId))) {
            return Optional.empty();
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        jdbc.query("SELECT product_id, quantity FROM basket_items WHERE basket_id = ? ORDER BY added_at, product_id",
                rs -> {
                    items.put(rs.getString("product_id"), rs.getInt("quantity"));
                }, basketId);
        return Optional.of(items);
    }

    @Override
    @Transactional
    public void set(String basketId, String productId, int quantity) {
        if (quantity <= 0) {
            jdbc.update("DELETE FROM basket_items WHERE basket_id = ? AND product_id = ?", basketId, productId);
        } else {
            jdbc.update("""
                    INSERT INTO basket_items (basket_id, product_id, quantity) VALUES (?, ?, ?)
                    ON CONFLICT (basket_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity""",
                    basketId, productId, quantity);
        }
        jdbc.update("UPDATE baskets SET updated_at = now() WHERE id = ?", basketId);
    }

    @Override
    @Transactional
    public void clear(String basketId) {
        jdbc.update("DELETE FROM basket_items WHERE basket_id = ?", basketId);
        jdbc.update("UPDATE baskets SET updated_at = now() WHERE id = ?", basketId);
    }

    @Override
    public void removeIdle(Instant cutoff) {
        jdbc.update("DELETE FROM baskets WHERE updated_at < ?", Timestamp.from(cutoff));
    }
}
