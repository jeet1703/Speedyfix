package com.speedyfixautosales.store.ebay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The eBay import against a real Postgres, with eBay and its image servers replaced by stand-ins. Needs Docker. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
class EbayImportServiceTest {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    static Path photos;

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("photos.dir", () -> photos.toString());
    }

    /** What the fake eBay search lists for the seller; tests set it. */
    static final List<EbayItemSource.Listing> SELLER_LISTINGS = new CopyOnWriteArrayList<>();

    /** Listings still on eBay that its search left out (eBay's search does that); tests set it. */
    static final List<String> MISSED_BY_SEARCH = new CopyOnWriteArrayList<>();

    /** eBay returns details for the ECU only: two photos and some item specifics. */
    @TestConfiguration
    static class FakeEbay {
        @Bean
        @Primary
        EbayItemSource fakeEbaySource() {
            return new EbayItemSource() {
                @Override
                public boolean enabled() {
                    return true;
                }

                @Override
                public Map<String, ItemDetails> fetch(List<String> itemNumbers) {
                    Map<String, ItemDetails> found = new HashMap<>();
                    if (itemNumbers.contains("365887388733")) {
                        found.put("365887388733", new ItemDetails("365887388733",
                                List.of("https://i.ebayimg.com/images/g/a/s-l1600.jpg", "https://i.ebayimg.com/images/g/b/s-l1600.jpg"),
                                List.of(new Aspect("Brand", "Nissan"), new Aspect("Manufacturer Part Number", "237104ED9B"),
                                        new Aspect("Type", "Engine Control Unit")),
                                "Nissan", "237104ED9B", "Tested, 12 month warranty.", "<h3>Nissan ECU</h3><p>Tested.</p>"));
                    }
                    return found;
                }

                @Override
                public Optional<String> sellerOf(String itemNumber) {
                    return Optional.of("speedyfixenginecomponents");
                }

                @Override
                public boolean listed(String itemNumber) {
                    return MISSED_BY_SEARCH.contains(itemNumber)
                            || SELLER_LISTINGS.stream().anyMatch(l -> l.itemNumber().equals(itemNumber));
                }

                @Override
                public List<Listing> sellerListings(String seller) {
                    return List.copyOf(SELLER_LISTINGS);
                }
            };
        }

        @Bean
        @Primary
        PhotoDownloader fakeDownloader() {
            return new PhotoDownloader() {
                @Override
                public byte[] download(String url) throws IOException {
                    BufferedImage img = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", out);
                    return out.toByteArray();
                }
            };
        }
    }

    @Autowired
    private EbayImportService importer;

    @Autowired
    private EbaySyncService sync;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @TempDir
    Path tmp;

    private Path csv() throws IOException {
        String header = "Item number,Title,Variation details,Custom label (SKU),Available quantity,Format,Currency,Start price,"
                + "Auction Buy It Now price,Reserve price,Current price,Sold quantity,Watchers,Bids,Start date,End date,"
                + "eBay category 1 name,eBay category 1 number,eBay category 2 name,eBay category 2 number,Condition\n";
        String rows = String.join("\n",
                "\"365887388733\",Nissan Qashqai J11 Auto Engine Control Unit 1.2L 237104ED9B 2014-2018,,,\"1\",\"FIXED_PRICE\",\"GBP\",194.99,,,194.99,\"2\",\"1\",\"0\",\"Sep-27-25 03:09:03 PDT\",\"Oct-27-26 04:09:03 PDT\",\"ECUs & Computer Modules\",\"33596\",\"\",\"\",\"Used\"",
                "\"365887559527\",\"Mercedes Benz A200D W177  Starter Motor 2.0L A2609060100 Minor Damaged 2018-2024\",,,\"0\",\"FIXED_PRICE\",\"GBP\",39.99,,,39.99,\"0\",\"1\",\"\",\"Sep-27-25 05:09:06 PDT\",\"Oct-27-26 06:09:06 PDT\",\"Starter Motors\",\"33578\",\"\",\"\",\"Used\"",
                "\"111111111111\",2019 Jaguar E-Pace 2.0 D150 R-Dynamic S,,,\"1\",\"CLASSIFIED_AD\",\"GBP\",24995,,,24995,\"0\",\"0\",\"\",\"Sep-27-25 05:09:06 PDT\",\"\",\"Cars\",\"9801\",\"\",\"\",\"Used\"");
        Path file = tmp.resolve("report.csv");
        Files.writeString(file, "﻿" + header + rows + "\n");
        return file;
    }

    @Test
    void syncAddsNewListingsFollowsPricesAndEndsWhatsGoneFromEbay() throws Exception {
        jdbc.update("""
                INSERT INTO products (id, name, category_id, condition, price, stock, status, ebay_item_id) VALUES
                    ('sync-repriced', 'Repriced part', 'engines', 'USED', 100, 1, 'LIVE', '900000000001'),
                    ('sync-sold-on-ebay', 'Sold on eBay', 'engines', 'USED', 50, 1, 'LIVE', '900000000002'),
                    ('sync-sold-here', 'Sold on the website', 'engines', 'USED', 70, 0, 'ENDED', '900000000003'),
                    ('sync-staff-only', 'Added by staff', 'engines', 'USED', 30, 1, 'LIVE', NULL),
                    ('sync-missed', 'Left out of the search', 'engines', 'USED', 60, 1, 'LIVE', '900000000005')""");
        // Ended by an earlier sync (both times set together) although eBay still lists it.
        jdbc.update("""
                INSERT INTO products (id, name, category_id, condition, price, stock, status, ebay_item_id, ebay_synced_at, updated_at)
                VALUES ('sync-wrongly-ended', 'Wrongly ended', 'engines', 'USED', 80, 1, 'ENDED', '900000000006',
                        '2026-11-01T10:00:00Z', '2026-11-01T10:00:00Z')""");
        MISSED_BY_SEARCH.clear();
        MISSED_BY_SEARCH.add("900000000005");
        LocalDate listed = LocalDate.of(2026, 10, 30);
        SELLER_LISTINGS.clear();
        SELLER_LISTINGS.addAll(List.of(
                new EbayItemSource.Listing("900000000001", "Repriced part", new BigDecimal("89.50"), "Used", "33615", "Engines", listed),
                new EbayItemSource.Listing("900000000003", "Sold on the website", new BigDecimal("70.00"), "Used", "33615", "Engines", listed),
                new EbayItemSource.Listing("900000000006", "Wrongly ended", new BigDecimal("80.00"), "Used", "33615", "Engines", listed),
                new EbayItemSource.Listing("900000000004", "Ford Focus MK3 1.0 EcoBoost Turbocharger 2012-2018", new BigDecimal("149.99"),
                        "Used", "33615", "Engines", listed)));

        EbaySyncService.Result r = sync.sync();

        assertThat(r.added()).isEqualTo(1);
        assertThat(r.repriced()).isEqualTo(1);
        assertThat(r.endingSkipped()).isFalse();
        assertThat(r.ended()).isEqualTo(1);
        assertThat(r.relisted()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT price FROM products WHERE id = 'sync-repriced'", BigDecimal.class)).isEqualByComparingTo("89.50");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = 'sync-sold-on-ebay'", String.class)).isEqualTo("ENDED");
        // Ended in the shop stays ended, even though eBay still lists it; staff's own parts are never touched.
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = 'sync-sold-here'", String.class)).isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = 'sync-staff-only'", String.class)).isEqualTo("LIVE");
        // Missing from the search but still on eBay: stays live. Wrongly ended by an earlier sync: back on sale.
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = 'sync-missed'", String.class)).isEqualTo("LIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = 'sync-wrongly-ended'", String.class)).isEqualTo("LIVE");
        assertThat(jdbc.queryForObject("SELECT status || ' ' || stock || ' ' || price FROM products WHERE ebay_item_id = '900000000004'", String.class))
                .isEqualTo("LIVE 1 149.99");

        // Running again changes nothing.
        EbaySyncService.Result again = sync.sync();
        assertThat(again.added() + again.repriced() + again.ended() + again.relisted()).isZero();

        // A live part whose photos failed earlier gets them on the next sync; its price stays the shop's.
        jdbc.update("INSERT INTO products (id, name, category_id, condition, price, stock, status, ebay_item_id) VALUES ('sync-no-photos', 'Nissan ECU', 'ecus-electronics', 'USED', 150, 1, 'LIVE', '365887388733')");
        SELLER_LISTINGS.add(new EbayItemSource.Listing("365887388733", "Nissan ECU", new BigDecimal("150.00"), "Used", "33596", "ECUs", listed));
        sync.sync();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_photos WHERE product_id = 'sync-no-photos' AND source = 'EBAY'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT price FROM products WHERE id = 'sync-no-photos'", BigDecimal.class)).isEqualByComparingTo("150.00");

        SELLER_LISTINGS.clear();
        MISSED_BY_SEARCH.clear();
        jdbc.update("DELETE FROM products WHERE id LIKE 'sync-%' OR ebay_item_id LIKE '90000000000%'");
    }

    @Test
    void importsListingsPhotosAndSpecificsAndIsSafeToRunAgain() throws Exception {
        Path file = csv();

        EbayImportService.Summary dry = importer.run(file, new EbayImportService.Options(true, 0, true, false));
        assertThat(dry.created()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE ebay_item_id IS NOT NULL", Integer.class)).isZero();

        EbayImportService.Summary first = importer.run(file, new EbayImportService.Options(false, 0, true, false));
        assertThat(first.created()).isEqualTo(2);
        assertThat(first.skipped()).isEqualTo(1);
        assertThat(first.ended()).isEqualTo(1);
        assertThat(first.photos()).isEqualTo(2);

        Map<String, Object> ecu = jdbc.queryForMap("SELECT * FROM products WHERE ebay_item_id = '365887388733'");
        assertThat(ecu.get("status")).isEqualTo("LIVE");
        assertThat(ecu.get("category_id")).isEqualTo("ecus-electronics");
        assertThat(ecu.get("part_number")).isEqualTo("237104ED9B");
        assertThat(ecu.get("brand")).isEqualTo("Nissan");
        assertThat(ecu.get("sub_type")).isEqualTo("Engine Control Unit");
        String id = (String) ecu.get("id");
        assertThat(Files.size(photos.resolve("listings/" + id + "/0-1200.jpg"))).isPositive();
        assertThat(Files.exists(photos.resolve("listings/" + id + "/1-400.jpg"))).isTrue();

        Map<String, Object> starter = jdbc.queryForMap("SELECT * FROM products WHERE ebay_item_id = '365887559527'");
        assertThat(starter.get("status")).isEqualTo("ENDED");
        assertThat(starter.get("part_number")).isEqualTo("A2609060100");
        assertThat(starter.get("brand")).isEqualTo("Mercedes Benz");

        // Running again updates, never duplicates, and doesn't download photos twice.
        EbayImportService.Summary second = importer.run(file, new EbayImportService.Options(false, 0, true, false));
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(2);
        assertThat(second.photos()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_photos WHERE product_id = ?", Integer.class, id)).isEqualTo(2);

        // The shop shows the live listing with its photos (small on cards, every photo full size on its page) and
        // eBay's item specifics and description, and not the ended one.
        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.imageUrl").value(endsWith("/api/images/listings/" + id + "/0-400.jpg")))
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[1].url").value(endsWith("/api/images/listings/" + id + "/1-1200.jpg")))
                .andExpect(jsonPath("$.photos[1].thumbnailUrl").value(endsWith("/api/images/listings/" + id + "/1-400.jpg")))
                .andExpect(jsonPath("$.specifics[?(@.name == 'Type')].value").value(org.hamcrest.Matchers.contains("Engine Control Unit")))
                .andExpect(jsonPath("$.description").value("Tested, 12 month warranty."))
                .andExpect(jsonPath("$.descriptionHtml").value("<h3>Nissan ECU</h3><p>Tested.</p>"))
                .andExpect(jsonPath("$.partNumber").value("237104ED9B"));
        mvc.perform(get("/api/products/" + starter.get("id"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/images/listings/" + id + "/0-1200.jpg")).andExpect(status().isOk());
        mvc.perform(get("/api/images/../../etc/passwd")).andExpect(status().isNotFound());
    }
}
