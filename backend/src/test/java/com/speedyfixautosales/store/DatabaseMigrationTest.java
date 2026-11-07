package com.speedyfixautosales.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.account.CustomerService;
import com.speedyfixautosales.store.account.GoogleTokenVerifier;
import com.speedyfixautosales.store.admin.AdminSettings;
import com.speedyfixautosales.store.basket.BasketService;
import com.speedyfixautosales.store.catalog.JdbcCatalogRepository;
import com.speedyfixautosales.store.catalog.ProductPhotoStore;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderService;
import com.speedyfixautosales.store.order.OrderStore;
import com.speedyfixautosales.store.partrequest.PartRequest;
import com.speedyfixautosales.store.partrequest.PartRequestService;
import com.speedyfixautosales.store.vehicle.LookupLog;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Autowired
    private CustomerService customers;

    @Autowired
    private BasketService baskets;

    @Autowired
    private OrderService orders;

    @Autowired
    private OrderStore orderStore;

    @Autowired
    private PartRequestService partRequests;

    @Autowired
    private AdminSettings settings;

    @Autowired
    private LookupLog lookups;

    @Test
    void customersBasketsAndOrdersAreKeptInTheDatabase() {
        jdbc.update("INSERT INTO products (id, name, category_id, condition, price, stock, status) VALUES ('kept', 'Kept part', 'engines', 'USED', 50, 3, 'LIVE')");
        catalog.invalidate();

        CustomerService.Customer c = customers.signIn(new GoogleTokenVerifier.GoogleIdentity("g-1", "Sam@Example.com", "Sam", null));
        assertThat(customers.signIn(new GoogleTokenVerifier.GoogleIdentity("g-1", "Sam@Example.com", "Sam Smith", null)).signIns()).isEqualTo(2);
        assertThat(customers.get("g-1").name()).isEqualTo("Sam Smith");
        customers.recordSearch(c.id(), "turbo");
        customers.recordSearch(c.id(), "ecu");
        assertThat(customers.recordSearch(c.id(), "TURBO")).containsExactly("TURBO", "ecu");

        String basket = baskets.create().id();
        baskets.addItem(basket, "kept", 2);
        assertThat(baskets.get(basket).itemCount()).isEqualTo(2);

        Order placed = orders.place(basket, new Order.Customer("Sam Smith", "sam@example.com", "07700 900123"),
                new Order.Address("1 High St", null, "Leeds", "LS1 1AA"), "Leave at the back", c.id());
        assertThat(baskets.get(basket).lines()).isEmpty();
        assertThat(catalog.product("kept")).get().satisfies(p -> assertThat(p.stock()).isEqualTo(1));

        // A guest order with the same email shows in the account too; another shopper's doesn't.
        String guestBasket = baskets.create().id();
        baskets.addItem(guestBasket, "kept", 1);
        Order guest = orders.place(guestBasket, new Order.Customer("Sam", "SAM@example.com", "07700 900123"),
                new Order.Address("1 High St", null, "Leeds", "LS1 1AA"), null, null);
        assertThat(orders.forCustomer(c.id(), c.email())).extracting(Order::reference)
                .containsExactly(guest.reference(), placed.reference());
        assertThat(orders.forCustomer("someone-else", "other@example.com")).isEmpty();

        // A PayPal payment's ids are kept with the order.
        orderStore.save(orders.get(placed.reference()).withPayment(new Order.Payment("PAYPAL-ORDER-1", "CAPTURE-1", Instant.now())));
        assertThat(orders.get(placed.reference()).payment()).satisfies(p -> {
            assertThat(p.paypalOrderId()).isEqualTo("PAYPAL-ORDER-1");
            assertThat(p.captureId()).isEqualTo("CAPTURE-1");
        });
        orders.setStatus(placed.reference(), Order.Status.AWAITING_DISPATCH, null, null);
        assertThat(orders.get(placed.reference()).payment().paidAt()).isNotNull();
        orders.setStatus(placed.reference(), Order.Status.DISPATCHED, "DPD", "15501234");
        Order back = orders.get(placed.reference().toLowerCase());
        assertThat(back.status()).isEqualTo(Order.Status.DISPATCHED);
        assertThat(back.dispatch().trackingNumber()).isEqualTo("15501234");
        assertThat(back.lines()).singleElement().satisfies(l -> {
            assertThat(l.productId()).isEqualTo("kept");
            assertThat(l.quantity()).isEqualTo(2);
            assertThat(l.lineTotal()).isEqualByComparingTo("100.00");
        });
        assertThat(back.notes()).isEqualTo("Leave at the back");
        assertThat(back.customerId()).isEqualTo("g-1");
        assertThat(jdbc.queryForObject("SELECT paid_at IS NOT NULL FROM orders WHERE reference = ?", Boolean.class, placed.reference())).isTrue();
    }

    @Test
    void partRequestsSettingsAndLookupsAreKeptInTheDatabase() {
        PartRequestService.Received r = partRequests.submit("AB12 CDE", "Gearbox", "6 speed", "Alex", "07700 900123", null,
                PartRequest.ReplyBy.WHATSAPP, List.of(new MockMultipartFile("photos", "box.png", "image/png", PNG)));
        assertThat(r.reference()).startsWith("PR-");
        assertThat(partRequests.get(r.reference().toLowerCase()).photos()).hasSize(1);
        assertThat(partRequests.photo(r.reference(), 0)).get().satisfies(p -> assertThat(p.data()).isEqualTo(PNG));
        assertThat(partRequests.setStatus(r.reference(), PartRequest.Status.QUOTED).status()).isEqualTo(PartRequest.Status.QUOTED);
        assertThat(partRequests.all()).extracting(PartRequest::reference).contains(r.reference());

        settings.set(new AdminSettings.Values("buyers@example.com"));
        assertThat(jdbc.queryForObject("SELECT buying_team_email FROM settings WHERE saved", String.class)).isEqualTo("buyers@example.com");

        Instant start = Instant.now().minus(1, ChronoUnit.MINUTES);
        lookups.record("nissan-qashqai-j11", false);
        lookups.record(null, false);
        lookups.record("nissan-qashqai-j11", true);
        assertThat(lookups.countSince(start)).isEqualTo(3);
        assertThat(lookups.noMatchSince(start)).isEqualTo(2);
        assertThat(lookups.noMatchByVehicle(start)).containsKeys("nissan-qashqai-j11", null);
    }

    @Test
    void migrationsCreateEveryTableAndSeedTheCategories() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name", String.class);
        assertThat(tables).contains("categories", "ebay_category_map", "products", "product_refinements", "product_aspects",
                "product_photos", "vehicles", "product_fitment", "lookup_log", "baskets", "basket_items", "orders",
                "order_items", "customers", "customer_searches", "part_requests", "part_request_photos", "settings",
                "import_runs");

        assertThat(jdbc.queryForList("SELECT id FROM categories WHERE popular ORDER BY sort_order", String.class))
                .containsExactly("engines", "gearboxes", "turbochargers", "accessories");
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
    void aCategoryShowsThePhotoOfItsMostValuableLivePart() {
        // Without a category image of its own (V10 gives each one a cut-out).
        jdbc.update("UPDATE categories SET image_key = NULL WHERE id = 'gearboxes'");
        jdbc.update("""
                INSERT INTO products (id, name, category_id, condition, price, stock, status) VALUES
                    ('cheap-box', 'Gearbox bolt kit', 'gearboxes', 'USED', 20, 1, 'LIVE'),
                    ('dear-box', 'Complete gearbox', 'gearboxes', 'USED', 900, 1, 'LIVE'),
                    ('sold-box', 'Sold gearbox', 'gearboxes', 'USED', 2000, 0, 'LIVE')""");
        jdbc.update("""
                INSERT INTO product_photos (id, product_id, position, r2_key, source) VALUES
                    ('c1', 'cheap-box', 0, 'listings/cheap-box/0-1200.jpg', 'EBAY'),
                    ('d1', 'dear-box', 0, 'listings/dear-box/0-1200.jpg', 'EBAY'),
                    ('s1', 'sold-box', 0, 'listings/sold-box/0-1200.jpg', 'EBAY')""");
        catalog.invalidate();
        assertThat(catalog.category("gearboxes")).get()
                .satisfies(c -> assertThat(c.imageUrl()).endsWith("/listings/dear-box/0-1200.jpg"));
    }

    @Autowired
    private com.speedyfixautosales.store.vehicle.VehicleService vehicleService;

    @Test
    void compatibilityRowsAreKeptInTheVehiclesTable() {
        var row = com.speedyfixautosales.store.vehicle.VehicleRange.of("Nissan", "Qashqai", 2014, 2021, "1461cc 81KW 110HP K9K 646", "diesel");
        String id = vehicleService.addRange(row).id();
        vehicleService.addRange(row);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehicles WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT engine_code || ' ' || fuel || ' ' || year_from || '-' || year_to FROM vehicles WHERE id = ?", String.class, id))
                .isEqualTo("K9K 646 diesel 2014-2021");
        assertThat(vehicleService.findById(id)).get().satisfies(v -> assertThat(v.engine()).isEqualTo("1.5L diesel, 110HP"));
        assertThat(vehicleService.all()).extracting(com.speedyfixautosales.store.vehicle.Vehicle::id).contains(id);
    }

    @Test
    void anEbayItemCanOnlyBeImportedOnce() {
        jdbc.update("INSERT INTO products (id, name, category_id, condition, ebay_item_id) VALUES ('a', 'Part A', 'engines', 'USED', '365887388733')");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO products (id, name, category_id, condition, ebay_item_id) VALUES ('b', 'Part B', 'engines', 'USED', '365887388733')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void emailAccountsKeepTheirPasswordHashAndOnlyOneMayUseEachAddress() {
        customers.signIn(new GoogleTokenVerifier.GoogleIdentity("g-ann", "ann@example.com", "Ann", null));
        assertThat(customers.accountFor("ANN@example.com")).contains(CustomerService.SignInMethod.GOOGLE);

        CustomerService.Customer ann = customers.createWithPassword("Ann@Example.com", "Ann", "pbkdf2$1$c2FsdA$aGFzaA");
        assertThat(ann.id()).startsWith(CustomerService.EMAIL_ID_PREFIX);
        assertThat(ann.signInMethod()).isEqualTo(CustomerService.SignInMethod.EMAIL);
        assertThat(customers.accountFor("ann@example.com")).contains(CustomerService.SignInMethod.EMAIL);
        assertThat(customers.passwordAccount("ann@EXAMPLE.com")).hasValueSatisfying(a -> {
            assertThat(a.customer().id()).isEqualTo(ann.id());
            assertThat(a.passwordHash()).isEqualTo("pbkdf2$1$c2FsdA$aGFzaA");
        });
        assertThat(customers.recordSignIn(ann.id()).signIns()).isEqualTo(2);
        assertThatThrownBy(() -> customers.createWithPassword("ann@example.com", "Ann again", "pbkdf2$1$c2FsdA$aGFzaA"))
                .isInstanceOf(com.speedyfixautosales.store.common.ConflictException.class);
    }
}
