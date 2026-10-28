package com.speedyfixautosales.store.catalog;

import com.speedyfixautosales.store.photo.PhotoUrls;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogue in PostgreSQL ("postgres" profile). The whole catalogue (a few thousand listings) is read into
 * memory and kept for a minute, or until anything changes, because the shop reads it many times per page.
 * A listing's first photo (lowest position) is its main image; with none yet it shows the placeholder. SAMPLE photos
 * (stock images in a demo copy of the database) are shown straight from their source address.
 */
@Repository
@Profile("postgres")
public class JdbcCatalogRepository implements CatalogRepository {

    private static final Duration CACHE_FOR = Duration.ofMinutes(1);

    private record Snapshot(List<Category> categories, Map<String, Product> products, Instant loadedAt) {
    }

    private final JdbcTemplate jdbc;
    private final PhotoUrls photoUrls;
    private volatile Snapshot snapshot;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile long generation;

    public JdbcCatalogRepository(JdbcTemplate jdbc, PhotoUrls photoUrls) {
        this.jdbc = jdbc;
        this.photoUrls = photoUrls;
    }

    @Override
    public List<Category> categories() {
        return current().categories();
    }

    @Override
    public Optional<Category> category(String id) {
        return current().categories().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @Override
    public List<Product> products() {
        return List.copyOf(current().products().values());
    }

    @Override
    public Optional<Product> product(String id) {
        return Optional.ofNullable(current().products().get(id));
    }

    @Override
    @Transactional
    public void save(Product p) {
        jdbc.update("""
                INSERT INTO products (id, name, category_id, condition, grade, price, part_number, engine_code, brand, sub_type,
                                      shipping, stock, best_seller, status, added_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, category_id = excluded.category_id,
                    condition = excluded.condition, grade = excluded.grade, price = excluded.price,
                    part_number = excluded.part_number, engine_code = excluded.engine_code, brand = excluded.brand,
                    sub_type = excluded.sub_type, shipping = excluded.shipping, stock = excluded.stock,
                    best_seller = excluded.best_seller, status = excluded.status, updated_at = now()
                """, p.id(), p.name(), p.categoryId(), p.condition().name(), p.grade(), p.price(), p.partNumber(),
                p.engineCode(), p.brand(), p.subType(), p.shipping().name(), p.stock(), p.bestSeller(), p.status().name(),
                Date.valueOf(p.addedOn()));
        jdbc.update("DELETE FROM product_refinements WHERE product_id = ?", p.id());
        p.refinements().forEach(r -> jdbc.update("INSERT INTO product_refinements (product_id, value) VALUES (?, ?)", p.id(), r));
        jdbc.update("DELETE FROM product_fitment WHERE product_id = ? AND source = 'MANUAL'", p.id());
        for (String vehicleId : p.fitsVehicleIds()) {
            jdbc.update("""
                    INSERT INTO product_fitment (product_id, vehicle_id, source, confidence) VALUES (?, ?, 'MANUAL', 'HIGH')
                    ON CONFLICT DO NOTHING""", p.id(), vehicleId);
        }
        invalidate();
    }

    @Override
    public Optional<String> sampleImage(String id) {
        return Optional.empty();
    }

    @Override
    public boolean reduceStock(String id, int quantity) {
        // One statement, so two buyers can't both take the last one-off part.
        int changed = jdbc.update("UPDATE products SET stock = stock - ?, updated_at = now() WHERE id = ? AND stock >= ?",
                quantity, id, quantity);
        invalidate();
        return changed == 1;
    }

    @Override
    public List<Slide> slides() {
        return StorefrontContent.SLIDES;
    }

    @Override
    public List<Feature> features() {
        return StorefrontContent.FEATURES;
    }

    @Override
    public List<MenuGroup> menu() {
        return StorefrontContent.MENU;
    }

    /** Loads the catalogue as soon as the app is up, so the first shopper doesn't wait for it. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread.startVirtualThread(this::current);
    }

    /** Drops the cached catalogue, so the next read sees changes made elsewhere (the eBay import, say). */
    public synchronized void invalidate() {
        generation++;
        snapshot = null;
    }

    /**
     * The cached catalogue. Only one load runs at a time; once a minute old it's refreshed in the background while
     * shoppers keep getting the previous copy, so nobody waits on the database except for the very first load.
     */
    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                if (snapshot == null) {
                    snapshot = load();
                }
                return snapshot;
            }
        }
        if (s.loadedAt().isBefore(Instant.now().minus(CACHE_FOR)) && refreshing.compareAndSet(false, true)) {
            long startedAt = generation;
            Thread.startVirtualThread(() -> {
                try {
                    Snapshot fresh = load();
                    synchronized (this) {
                        // A save during the load makes this copy out of date; the next read loads afresh instead.
                        if (generation == startedAt && snapshot != null) {
                            snapshot = fresh;
                        }
                    }
                } finally {
                    refreshing.set(false);
                }
            });
        }
        return s;
    }

    /** The category's own banner when staff have set one, else a sample photo, else the placeholder. */
    private String categoryImage(String id, String key) {
        if (key != null) {
            return photoUrls.url(key);
        }
        return StorefrontContent.CATEGORY_SAMPLE_PHOTOS.getOrDefault(id, photoUrls.url(Images.PLACEHOLDER_KEY));
    }

    private Snapshot load() {
        List<Category> categories = jdbc.query("SELECT id, name, popular, sort_order, image_key FROM categories ORDER BY sort_order",
                (rs, i) -> new Category(rs.getString("id"), rs.getString("name"), categoryImage(rs.getString("id"), rs.getString("image_key")),
                        rs.getBoolean("popular"), rs.getInt("sort_order")));

        Map<String, List<String>> refinements = new HashMap<>();
        jdbc.query("SELECT product_id, value FROM product_refinements ORDER BY value", (rs, i) ->
                refinements.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2)));
        Map<String, List<String>> fitment = new HashMap<>();
        jdbc.query("SELECT product_id, vehicle_id FROM product_fitment", (rs, i) ->
                fitment.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2)));

        Map<String, Product> products = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.*, ph.r2_key AS main_photo, ph.source AS photo_source, ph.source_url AS photo_url
                FROM products p
                LEFT JOIN LATERAL (SELECT r2_key, source, source_url FROM product_photos
                                   WHERE product_id = p.id ORDER BY position LIMIT 1) ph ON true
                ORDER BY p.added_on DESC, p.id
                """, rs -> {
            String id = rs.getString("id");
            String photo = rs.getString("main_photo");
            String image = "SAMPLE".equals(rs.getString("photo_source")) ? rs.getString("photo_url")
                    : photoUrls.url(photo != null ? photo : Images.PLACEHOLDER_KEY);
            products.put(id, new Product(id, rs.getString("name"), rs.getString("category_id"),
                    Condition.valueOf(rs.getString("condition")), rs.getString("grade"), rs.getBigDecimal("price"),
                    rs.getString("part_number"), rs.getString("engine_code"), rs.getString("brand"), rs.getString("sub_type"),
                    refinements.getOrDefault(id, List.of()), image,
                    fitment.getOrDefault(id, List.of()), Shipping.valueOf(rs.getString("shipping")), rs.getInt("stock"),
                    rs.getBoolean("best_seller"), rs.getDate("added_on").toLocalDate(), ProductStatus.valueOf(rs.getString("status"))));
        });
        return new Snapshot(categories, products, Instant.now());
    }
}
