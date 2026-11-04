package com.speedyfixautosales.store.ebay;

import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.Product;
import com.speedyfixautosales.store.vehicle.VehicleRange;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Brings each eBay listing's compatibility table into the shop: its rows become vehicle rows (make, model, years and
 * engine, like the ones staff pick in the listing editor) on the matching part. Rows staff added stay; running it
 * again only adds what's new. Runs in the background; staff start it from the Listings page.
 */
@Service
@Profile("postgres")
public class EbayCompatibilityImport {

    private static final Logger log = LoggerFactory.getLogger(EbayCompatibilityImport.class);

    private final JdbcTemplate jdbc;
    private final EbayTradingClient ebay;
    private final CatalogRepository catalog;
    private final VehicleService vehicles;
    private volatile Status status = new Status(false, 0, 0, 0, 0, 0, null, null, List.of());

    /**
     * checked of total listings so far; updated is parts that got new vehicle rows, rows the rows added, noTable the
     * listings with no compatibility table on eBay. problems are the listings eBay wouldn't give (item and reason).
     */
    public record Status(boolean running, int checked, int total, int updated, int rows, int noTable, Instant finishedAt,
                         String error, List<String> problems) {
    }

    public EbayCompatibilityImport(JdbcTemplate jdbc, EbayTradingClient ebay, CatalogRepository catalog, VehicleService vehicles) {
        this.jdbc = jdbc;
        this.ebay = ebay;
        this.catalog = catalog;
        this.vehicles = vehicles;
    }

    public boolean available() {
        return ebay.enabled();
    }

    public Status status() {
        return status;
    }

    /** False when an import is already running. */
    public synchronized boolean start() {
        if (status.running()) {
            return false;
        }
        status = new Status(true, 0, 0, 0, 0, 0, null, null, List.of());
        Thread t = new Thread(this::run, "ebay-compatibility-import");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private record Listing(String productId, String itemId) {
    }

    private void run() {
        List<Listing> listings = jdbc.query("SELECT id, ebay_item_id FROM products WHERE ebay_item_id IS NOT NULL ORDER BY id",
                (rs, i) -> new Listing(rs.getString("id"), rs.getString("ebay_item_id")));
        int checked = 0, updated = 0, rows = 0, noTable = 0, failedInARow = 0;
        List<String> problems = new ArrayList<>();
        String error = null;
        for (Listing l : listings) {
            try {
                List<Map<String, String>> table = ebay.compatibility(l.itemId());
                failedInARow = 0;
                if (table.isEmpty()) {
                    noTable++;
                } else {
                    int added = addTo(l.productId(), ranges(table));
                    if (added > 0) {
                        updated++;
                        rows += added;
                    }
                }
            } catch (IOException | RuntimeException e) {
                problems.add(l.itemId() + ": " + e.getMessage());
                // A bad token or keys fail every call: stop rather than spend the day's allowance on errors.
                if (++failedInARow >= 5) {
                    error = "eBay refused 5 listings in a row (" + e.getMessage() + "). Check EBAY_DEV_ID and EBAY_USER_TOKEN.";
                    break;
                }
            }
            checked++;
            status = new Status(true, checked, listings.size(), updated, rows, noTable, null, null, List.copyOf(tail(problems)));
        }
        log.info("eBay compatibility import: {} of {} checked, {} parts updated, {} rows added, {} without a table, {} problems",
                checked, listings.size(), updated, rows, noTable, problems.size());
        status = new Status(false, checked, listings.size(), updated, rows, noTable, Instant.now(), error, List.copyOf(tail(problems)));
    }

    private static List<String> tail(List<String> problems) {
        return problems.size() <= 20 ? problems : problems.subList(problems.size() - 20, problems.size());
    }

    /** Adds the rows the part doesn't have yet; returns how many. */
    private int addTo(String productId, List<VehicleRange> ranges) {
        Product p = catalog.product(productId).orElse(null);
        if (p == null) {
            return 0;
        }
        Set<String> ids = new LinkedHashSet<>(p.fitsVehicleIds());
        int before = ids.size();
        for (VehicleRange r : ranges) {
            ids.add(vehicles.addRange(r).id());
        }
        if (ids.size() == before) {
            return 0;
        }
        catalog.save(new Product(p.id(), p.name(), p.categoryId(), p.condition(), p.grade(), p.price(), p.partNumber(),
                p.engineCode(), p.brand(), p.subType(), p.refinements(), p.imageUrl(), List.copyOf(ids), p.shipping(),
                p.stock(), p.bestSeller(), p.addedOn(), p.status(), p.salePercent()));
        return ids.size() - before;
    }

    /**
     * eBay's table has a row per make, model, year, engine and variant (often hundreds); the shop keeps one row per
     * make, model and engine for each run of consecutive years, with the fuel where the variants agree on one.
     */
    static List<VehicleRange> ranges(List<Map<String, String>> table) {
        record Key(String make, String model, String engine) {
        }
        Map<Key, TreeSet<Integer>> years = new LinkedHashMap<>();
        Map<Key, Set<String>> fuels = new LinkedHashMap<>();
        for (Map<String, String> row : table) {
            String make = row.getOrDefault("Car Make", row.get("Make"));
            String model = row.get("Model");
            Integer year = year(row.getOrDefault("Cars Year", row.get("Year")));
            if (make == null || model == null || year == null) {
                continue;
            }
            Key k = new Key(make, model, row.get("Engine"));
            years.computeIfAbsent(k, x -> new TreeSet<>()).add(year);
            fuels.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(fuel(row.get("Variant")));
        }
        List<VehicleRange> out = new ArrayList<>();
        years.forEach((k, ys) -> {
            Set<String> f = fuels.get(k);
            String fuel = f.size() == 1 ? f.iterator().next() : null;
            Integer from = null, prev = null;
            for (int y : ys) {
                if (from == null) {
                    from = y;
                } else if (y != prev + 1) {
                    out.add(VehicleRange.of(k.make(), k.model(), from, prev, k.engine(), fuel));
                    from = y;
                }
                prev = y;
            }
            out.add(VehicleRange.of(k.make(), k.model(), from, prev, k.engine(), fuel));
        });
        return out;
    }

    private static Integer year(String s) {
        if (s == null || !s.trim().matches("\\d{4}")) {
            return null;
        }
        return Integer.parseInt(s.trim());
    }

    /** "Diesel SUV" is diesel; null when the variant doesn't say. */
    private static String fuel(String variant) {
        if (variant == null) {
            return null;
        }
        String v = variant.toLowerCase(Locale.UK);
        for (String f : List.of("hybrid", "electric", "diesel", "petrol")) {
            if (v.contains(f)) {
                return f;
            }
        }
        return null;
    }
}
