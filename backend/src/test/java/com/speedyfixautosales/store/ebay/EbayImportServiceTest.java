package com.speedyfixautosales.store.ebay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                                "Nissan", "237104ED9B", "Tested, 12 month warranty."));
                    }
                    return found;
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

        // The shop shows the live listing with its photo, and not the ended one.
        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.imageUrl").value(endsWith("/api/images/listings/" + id + "/0-1200.jpg")))
                .andExpect(jsonPath("$.partNumber").value("237104ED9B"));
        mvc.perform(get("/api/products/" + starter.get("id"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/images/listings/" + id + "/0-1200.jpg")).andExpect(status().isOk());
        mvc.perform(get("/api/images/../../etc/passwd")).andExpect(status().isNotFound());
    }
}
