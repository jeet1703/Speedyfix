package com.speedyfixautosales.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbc;

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
    void anEbayItemCanOnlyBeImportedOnce() {
        jdbc.update("INSERT INTO products (id, name, category_id, condition, ebay_item_id) VALUES ('a', 'Part A', 'engines', 'USED', '365887388733')");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO products (id, name, category_id, condition, ebay_item_id) VALUES ('b', 'Part B', 'engines', 'USED', '365887388733')"))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
