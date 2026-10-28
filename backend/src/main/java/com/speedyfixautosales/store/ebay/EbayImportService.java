package com.speedyfixautosales.store.ebay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.JdbcCatalogRepository;
import com.speedyfixautosales.store.photo.PhotoProcessor;
import com.speedyfixautosales.store.photo.PhotoStorage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Imports eBay's "All active listings" report into the catalogue. Safe to run again: listings are matched on their
 * eBay item number and updated, never duplicated.
 *
 * <ol>
 *   <li>Each fixed-price listing becomes a part: LIVE when in stock, ENDED at quantity 0. Auctions and classified ads
 *       (the cars for sale) are skipped.</li>
 *   <li>The eBay category picks the shop category through ebay_category_map (staff can change it there).</li>
 *   <li>The title gives the make, part number, engine size and years when eBay's item specifics don't.</li>
 *   <li>With eBay keys set, the Browse API adds item specifics and all photos; the photos are downloaded once,
 *       resized and stored under storage/listings/{part}/.</li>
 * </ol>
 */
@Service
@Profile("postgres")
public class EbayImportService {

    private static final Logger log = LoggerFactory.getLogger(EbayImportService.class);
    private static final int BATCH = 20;
    private static final int MAX_PHOTOS = 12;
    private static final Set<String> NOT_A_VALUE = Set.of("does not apply", "n/a", "na", "unbranded", "unknown", "-");

    /** dryRun reports what would happen without saving. limit 0 means every row. */
    public record Options(boolean dryRun, int limit, boolean details, boolean refreshPhotos) {
    }

    public record Summary(long runId, int rows, int created, int updated, int ended, int skipped, int detailsFound,
                          int photos, int withPartNumber, boolean detailsSkipped, List<String> problems) {
    }

    private final JdbcTemplate jdbc;
    private final EbayItemSource ebay;
    private final PhotoDownloader downloader;
    private final PhotoProcessor processor;
    private final PhotoStorage storage;
    private final CatalogRepository catalog;
    private final ObjectMapper json = new ObjectMapper();

    public EbayImportService(JdbcTemplate jdbc, EbayItemSource ebay, PhotoDownloader downloader, PhotoProcessor processor,
                             PhotoStorage storage, CatalogRepository catalog) {
        this.jdbc = jdbc;
        this.ebay = ebay;
        this.downloader = downloader;
        this.processor = processor;
        this.storage = storage;
        this.catalog = catalog;
    }

    public Summary run(Path csv, Options options) throws IOException {
        List<EbayCsv.Row> rows = EbayCsv.read(csv);
        if (options.limit() > 0 && rows.size() > options.limit()) {
            rows = rows.subList(0, options.limit());
        }
        Long runId = jdbc.queryForObject("INSERT INTO import_runs (file_name, dry_run) VALUES (?, ?) RETURNING id", Long.class,
                csv.getFileName().toString(), options.dryRun());
        List<String> problems = new ArrayList<>();
        int created = 0, updated = 0, ended = 0, skipped = 0, withPartNumber = 0;
        Map<String, String> productByItem = new LinkedHashMap<>();

        for (EbayCsv.Row row : rows) {
            String skip = skipReason(row);
            if (skip != null) {
                skipped++;
                if (!skip.startsWith("auction") && !skip.startsWith("classified")) {
                    problems.add("Line " + row.line() + " (" + row.itemNumber() + "): " + skip);
                }
                continue;
            }
            boolean live = row.quantity() > 0;
            if (!live) {
                ended++;
            }
            TitleParser.Parsed parsed = TitleParser.parse(row.title());
            if (parsed.partNumber() != null) {
                withPartNumber++;
            }
            String categoryId = category(row, options.dryRun());
            Optional<String> existing = jdbc.queryForList("SELECT id FROM products WHERE ebay_item_id = ?", String.class,
                    row.itemNumber()).stream().findFirst();
            String id = existing.orElseGet(() -> newId(row.title(), row.itemNumber()));
            if (existing.isPresent()) {
                updated++;
            } else {
                created++;
            }
            productByItem.put(row.itemNumber(), id);
            if (!options.dryRun()) {
                upsert(id, existing.isPresent(), row, parsed, categoryId, live);
            }
        }

        int detailsFound = 0;
        int photos = 0;
        boolean detailsSkipped = !options.details() || !ebay.enabled() || options.dryRun();
        if (!detailsSkipped) {
            List<String> items = new ArrayList<>(productByItem.keySet());
            for (int i = 0; i < items.size(); i += BATCH) {
                List<String> batch = items.subList(i, Math.min(i + BATCH, items.size()));
                Map<String, EbayItemSource.ItemDetails> found = ebay.fetch(batch);
                for (String item : batch) {
                    EbayItemSource.ItemDetails d = found.get(item);
                    if (d == null) {
                        problems.add("eBay didn’t return " + item + " (ended, or hidden while out of stock)");
                        continue;
                    }
                    detailsFound++;
                    photos += applyDetails(productByItem.get(item), d, options.refreshPhotos(), problems);
                }
                log.info("eBay details: {} of {} listings done, {} photos so far", Math.min(i + BATCH, items.size()), items.size(), photos);
            }
        } else if (options.details() && !ebay.enabled()) {
            problems.add(0, "eBay keys not set (EBAY_CLIENT_ID, EBAY_CLIENT_SECRET): photos and item specifics were skipped");
        }

        jdbc.update("UPDATE import_runs SET finished_at = now(), created = ?, updated = ?, skipped = ?, photos = ?, errors = ?::jsonb WHERE id = ?",
                created, updated, skipped, photos, toJson(problems), runId);
        if (catalog instanceof JdbcCatalogRepository db) {
            db.invalidate();
        }
        return new Summary(runId, rows.size(), created, updated, ended, skipped, detailsFound, photos, withPartNumber,
                detailsSkipped, problems);
    }

    private static String skipReason(EbayCsv.Row row) {
        if (row.itemNumber().isBlank()) {
            return "no item number";
        }
        if ("AUCTION".equals(row.format())) {
            return "auction (the shop sells at fixed prices)";
        }
        if ("CLASSIFIED_AD".equals(row.format())) {
            return "classified ad (a car for sale; cars aren’t imported yet)";
        }
        if (!"FIXED_PRICE".equals(row.format())) {
            return "unknown format " + row.format();
        }
        if (row.price() == null || row.price().signum() <= 0) {
            return "no price";
        }
        if (condition(row.condition()) == null) {
            return "unknown condition " + row.condition();
        }
        return null;
    }

    private static String condition(String ebayCondition) {
        return switch (ebayCondition.toLowerCase(Locale.UK)) {
            case "used", "for parts or not working" -> "USED";
            case "remanufactured", "seller refurbished", "refurbished" -> "REMANUFACTURED";
            case "new", "new other (see details)", "new with defects" -> "NEW";
            default -> null;
        };
    }

    /** The shop category for this eBay category, recording a suggestion the first time it's seen. */
    private String category(EbayCsv.Row row, boolean dryRun) {
        List<String> mapped = jdbc.queryForList("SELECT category_id FROM ebay_category_map WHERE ebay_category_id = ?",
                String.class, row.categoryId());
        if (!mapped.isEmpty() && mapped.get(0) != null) {
            return mapped.get(0);
        }
        String suggested = CategoryMapper.suggest(row.categoryName());
        if (!dryRun && mapped.isEmpty()) {
            jdbc.update("INSERT INTO ebay_category_map (ebay_category_id, ebay_name, category_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    row.categoryId(), row.categoryName(), suggested);
        }
        return suggested;
    }

    private void upsert(String id, boolean exists, EbayCsv.Row row, TitleParser.Parsed parsed, String categoryId, boolean live) {
        String status = live ? "LIVE" : "ENDED";
        String shipping = CategoryMapper.PALLET_CATEGORIES.contains(categoryId) ? "PALLET" : "PARCEL";
        String name = row.title().length() > 200 ? row.title().substring(0, 200) : row.title();
        if (exists) {
            // Staff edits to part number and brand are kept; eBay's price, stock and status always win.
            jdbc.update("""
                    UPDATE products SET name = ?, category_id = ?, condition = ?, price = ?, stock = ?, status = ?, sold_count = ?,
                        best_seller = ?, part_number = COALESCE(part_number, ?), brand = COALESCE(brand, ?), shipping = ?,
                        ebay_synced_at = now(), updated_at = now()
                    WHERE id = ?""",
                    name, categoryId, condition(row.condition()), row.price(), row.quantity(), status, row.sold(), row.sold() > 0,
                    parsed.partNumber(), parsed.make(), shipping, id);
        } else {
            jdbc.update("""
                    INSERT INTO products (id, name, category_id, condition, price, part_number, brand, shipping, stock, best_seller,
                                          sold_count, status, added_on, ebay_item_id, ebay_synced_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())""",
                    id, name, categoryId, condition(row.condition()), row.price(), parsed.partNumber(), parsed.make(), shipping,
                    row.quantity(), row.sold() > 0, row.sold(), status,
                    Date.valueOf(row.startDate() != null ? row.startDate() : LocalDate.now()), row.itemNumber());
        }
    }

    /** Saves item specifics and photos. Returns how many photos were stored. */
    private int applyDetails(String productId, EbayItemSource.ItemDetails d, boolean refreshPhotos, List<String> problems) {
        jdbc.update("DELETE FROM product_aspects WHERE product_id = ?", productId);
        for (EbayItemSource.Aspect a : d.aspects()) {
            if (!a.name().isBlank() && !a.value().isBlank()) {
                jdbc.update("INSERT INTO product_aspects (product_id, name, value) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                        productId, cut(a.name(), 100), cut(a.value(), 500));
            }
        }
        String brand = firstReal(d.brand(), aspect(d, "Brand"));
        String partNumber = firstReal(d.mpn(), aspect(d, "Manufacturer Part Number"), aspect(d, "Reference OE/OEM Number"),
                aspect(d, "OE/OEM Part Number"));
        String subType = firstReal(aspect(d, "Type"), aspect(d, "Sub-Type"));
        // eBay's specifics are more reliable than the title, so they replace what the title gave.
        jdbc.update("""
                UPDATE products SET brand = COALESCE(?, brand), part_number = COALESCE(?, part_number), sub_type = COALESCE(?, sub_type),
                    description = COALESCE(?, description), updated_at = now() WHERE id = ?""",
                cut(brand, 60), partNumber == null ? null : cut(partNumber.split(",")[0].trim().toUpperCase(Locale.UK), 40),
                cut(subType, 60), d.description(), productId);

        boolean hasPhotos = !jdbc.queryForList("SELECT id FROM product_photos WHERE product_id = ? AND source = 'EBAY'",
                String.class, productId).isEmpty();
        if (hasPhotos && !refreshPhotos) {
            return 0;
        }
        // Real eBay photos replace any stock (SAMPLE) photos a demo copy of the database gave the listing.
        jdbc.update("DELETE FROM product_photos WHERE product_id = ? AND source IN ('EBAY', 'SAMPLE')", productId);
        int stored = 0;
        for (String url : d.imageUrls().stream().limit(MAX_PHOTOS).toList()) {
            try {
                BufferedImage img = processor.decode(downloader.download(url));
                PhotoProcessor.Resized large = processor.resize(img, PhotoProcessor.LARGE);
                PhotoProcessor.Resized small = processor.resize(img, PhotoProcessor.SMALL);
                String key = "listings/" + productId + "/" + stored + "-" + PhotoProcessor.LARGE + ".jpg";
                storage.put(key, large.jpeg());
                storage.put("listings/" + productId + "/" + stored + "-" + PhotoProcessor.SMALL + ".jpg", small.jpeg());
                jdbc.update("""
                        INSERT INTO product_photos (id, product_id, position, r2_key, width, height, source, source_url)
                        VALUES (?, ?, ?, ?, ?, ?, 'EBAY', ?)""",
                        UUID.randomUUID().toString(), productId, stored, key, large.width(), large.height(), url);
                stored++;
            } catch (IOException | RuntimeException e) {
                problems.add("Photo for " + d.itemNumber() + " not saved (" + e.getMessage() + ")");
            }
        }
        return stored;
    }

    private static String aspect(EbayItemSource.ItemDetails d, String name) {
        return d.aspects().stream().filter(a -> a.name().equalsIgnoreCase(name)).map(EbayItemSource.Aspect::value).findFirst().orElse(null);
    }

    private static String firstReal(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !NOT_A_VALUE.contains(v.trim().toLowerCase(Locale.UK))) {
                return v.trim();
            }
        }
        return null;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** A readable, stable id: the title as a slug plus the eBay item number, e.g. "nissan-qashqai-j11-auto-ecu-365887388733". */
    static String newId(String title, String itemNumber) {
        String slug = title.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-+$", "");
        }
        return (slug.isEmpty() ? "part" : slug) + "-" + itemNumber;
    }

    private String toJson(List<String> problems) {
        try {
            return json.writeValueAsString(problems.size() > 500 ? problems.subList(0, 500) : problems);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
