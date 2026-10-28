package com.speedyfixautosales.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.catalog.JdbcCatalogRepository;
import com.speedyfixautosales.store.catalog.ProductPhotoStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the Flyway migrations against a real, throwaway Postgres 16 (the version used locally and live).
 * Needs Docker; skipped where Docker isn't running.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationTest {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    /** Enough of a PNG for the signature check. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductPhotoStore photoStore;

    @Autowired
    private JdbcCatalogRepository catalog;

    @Test
    void migrationsCreateEveryTableAndSeedTheCategories() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name", String.class);
        assertThat(tables).contains("categories", "ebay_category_map", "products", "product_refinements", "product_aspects",
                "product_photos", "vehicles", "product_fitment", "lookup_log", "baskets", "basket_items", "orders",
                "order_items", "customers", "customer_searches", "part_requests", "part_request_photos", "settings",
                "import_runs");

        assertThat(jdbc.queryForList("SELECT id FROM categories WHERE popular ORDER BY sort_order", String.class))
                .containsExactly("engines", "gearboxes", "accessories", "turbochargers");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settings", Integer.class)).isEqualTo(1);
    }

    @Test
    void uploadedPhotosAreKeptInTheDatabaseAndBecomeTheMainImage() {
        jdbc.update("INSERT INTO products (id, name, category_id, condition, price, status) VALUES ('up', 'Uploaded part', 'engines', 'USED', 10, 'LIVE')");
        // A photo from eBay at position 0 already; staff uploads go in front of it.
        jdbc.update("INSERT INTO product_photos (id, product_id, position, r2_key, source) VALUES ('eb1', 'up', 0, 'listings/up/eb1.jpg', 'EBAY')");

        List<String> ids = photoStore.update("up", List.of(), List.of(new MockMultipartFile("photos", "a.png", "image/png", PNG),
                new MockMultipartFile("photos", "b.png", "image/png", PNG)));
        assertThat(ids).hasSize(2);
        assertThat(photoStore.get(ids.get(0))).get().satisfies(p -> {
            assertThat(p.contentType()).isEqualTo("image/png");
            assertThat(p.data()).isEqualTo(PNG);
        });

        // Keeping only the second reorders it to the front and deletes the first, file and all; eBay's photo stays.
        assertThat(photoStore.update("up", List.of(ids.get(1)), List.of())).containsExactly(ids.get(1));
        assertThat(photoStore.get(ids.get(0))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_photo_files", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_photos WHERE source = 'EBAY'", Integer.class)).isEqualTo(1);

        catalog.invalidate();
        assertThat(catalog.product("up")).get().satisfies(p -> {
            assertThat(p.imageUrl()).endsWith("/api/product-photos/" + ids.get(1));
            assertThat(p.hasPhoto()).isTrue();
        });
    }

    @Test
    void anEbayItemCanOnlyBeImportedOnce() {
        jdbc.update("INSERT INTO products (id, name, category_id, condition, ebay_item_id) VALUES ('a', 'Part A', 'engines', 'USED', '365887388733')");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO products (id, name, category_id, condition, ebay_item_id) VALUES ('b', 'Part B', 'engines', 'USED', '365887388733')"))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
