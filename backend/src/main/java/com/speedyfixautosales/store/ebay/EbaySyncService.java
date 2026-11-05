package com.speedyfixautosales.store.ebay;

import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.JdbcCatalogRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps the shop in step with the seller's eBay listings, every EBAY_SYNC_INTERVAL (30 minutes by default) and a
 * couple of minutes after start-up:
 * <ul>
 *   <li>new eBay listings are added, with their photos and item specifics (through EbayImportService);</li>
 *   <li>a listing's price follows eBay's;</li>
 *   <li>a live part whose listing is no longer on eBay (sold or ended) is ended in the shop, once eBay confirms
 *       the listing itself is gone (its search sometimes leaves listings out);</li>
 *   <li>a part the sync ended whose listing is still on eBay, with stock and untouched by staff since, is live again.</li>
 * </ul>
 * Titles, stock and anything staff changed are left alone, and a part ended in the shop is never brought back, so a
 * part sold on the website can't reappear while its eBay listing is still up. If more than a quarter of the live
 * parts seem to have gone from eBay at once, nothing is ended: that's more likely an eBay hiccup than a sale.
 */
@Service
@Profile("postgres")
public class EbaySyncService {

    private static final Logger log = LoggerFactory.getLogger(EbaySyncService.class);
    static final double MAX_ENDED_SHARE = 0.25;
    static final int PHOTO_RETRIES_PER_RUN = 20;

    public record Result(Instant at, int listingsOnEbay, int added, int repriced, int ended, int relisted,
                         boolean endingSkipped, List<String> problems) {
    }

    private final JdbcTemplate jdbc;
    private final EbayItemSource ebay;
    private final EbayImportService importer;
    private final CatalogRepository catalog;
    private final boolean enabled;
    private volatile String seller;
    private volatile Result last;
    private volatile boolean running;
    private volatile Instant startedAt;
    private volatile String lastError;

    /**
     * For the admin: whether the sync can run here (eBay keys set, sync switched on), whether one is running now,
     * the last one that finished since start-up, and why the last attempt failed (null if it didn't).
     */
    public record Status(boolean available, boolean running, Instant startedAt, Result last, String lastError) {
    }

    public EbaySyncService(JdbcTemplate jdbc, EbayItemSource ebay, EbayImportService importer, CatalogRepository catalog,
                           @Value("${ebay.seller:}") String seller, @Value("${ebay.sync:true}") boolean enabled,
                           @Value("${import.ebay.csv:}") String importing) {
        this.jdbc = jdbc;
        this.ebay = ebay;
        this.importer = importer;
        this.catalog = catalog;
        this.seller = seller.isBlank() ? null : seller.trim();
        // Not while the command-line import runs: that run is already doing the work.
        this.enabled = enabled && importing.isBlank();
    }

    /** The last sync, or null if none has run since start-up. */
    public Result last() {
        return last;
    }

    public Status status() {
        return new Status(enabled && ebay.enabled(), running, startedAt, last, lastError);
    }

    /** Starts a sync in the background (one can take a few minutes when there are new listings). False if one is running. */
    public boolean startNow() {
        if (running || !enabled || !ebay.enabled()) {
            return false;
        }
        running = true;
        Thread.startVirtualThread(() -> {
            try {
                sync();
            } catch (IOException | RuntimeException e) {
                log.warn("eBay sync (started by staff) failed: {}", e.getMessage());
            }
        });
        return true;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${ebay.sync-interval:PT30M}")
    void scheduled() {
        if (!enabled || !ebay.enabled()) {
            return;
        }
        try {
            Result r = sync();
            log.info("eBay sync: {} listings on eBay, {} added, {} repriced, {} ended, {} relisted{}", r.listingsOnEbay(), r.added(),
                    r.repriced(), r.ended(), r.relisted(), r.endingSkipped() ? " (ending skipped: too many missing at once)" : "");
        } catch (IOException | RuntimeException e) {
            log.warn("eBay sync failed: {}", e.getMessage());
        }
    }

    public synchronized Result sync() throws IOException {
        running = true;
        startedAt = Instant.now();
        try {
            Result r = syncOnce();
            lastError = null;
            return r;
        } catch (IOException | RuntimeException e) {
            lastError = e.getMessage();
            throw e;
        } finally {
            running = false;
        }
    }

    private Result syncOnce() throws IOException {
        if (!ebay.enabled()) {
            throw new IOException("eBay keys not set (EBAY_CLIENT_ID, EBAY_CLIENT_SECRET).");
        }
        String who = seller();
        List<EbayItemSource.Listing> listings = ebay.sellerListings(who);
        if (listings.isEmpty()) {
            throw new IOException("eBay returned no listings for " + who + "; nothing changed.");
        }
        Map<String, EbayItemSource.Listing> onEbay = new LinkedHashMap<>();
        listings.forEach(l -> onEbay.put(l.itemNumber(), l));

        record Known(String id, String status, BigDecimal price) {
        }
        Map<String, Known> known = new HashMap<>();
        jdbc.query("SELECT id, ebay_item_id, status, price FROM products WHERE ebay_item_id IS NOT NULL",
                rs -> {
                    known.put(rs.getString("ebay_item_id"), new Known(rs.getString("id"), rs.getString("status"), rs.getBigDecimal("price")));
                });

        int repriced = 0;
        Map<String, String> gone = new LinkedHashMap<>();
        long live = 0;
        for (Map.Entry<String, Known> e : known.entrySet()) {
            Known k = e.getValue();
            if ("LIVE".equals(k.status())) {
                live++;
            }
            EbayItemSource.Listing l = onEbay.get(e.getKey());
            if (l == null) {
                if ("LIVE".equals(k.status())) {
                    gone.put(e.getKey(), k.id());
                }
            } else if (l.price() != null && l.price().signum() > 0 && (k.price() == null || l.price().compareTo(k.price()) != 0)) {
                jdbc.update("UPDATE products SET price = ?, ebay_synced_at = now(), updated_at = now() WHERE id = ?", l.price(), k.id());
                repriced++;
            }
        }
        boolean endingSkipped = !gone.isEmpty() && gone.size() > Math.max(5, live * MAX_ENDED_SHARE);
        List<String> problems = new ArrayList<>();
        int ended = 0;
        if (!endingSkipped) {
            for (Map.Entry<String, String> g : gone.entrySet()) {
                try {
                    if (ebay.listed(g.getKey())) {
                        continue;
                    }
                } catch (IOException e) {
                    problems.add("Couldn’t check eBay listing " + g.getKey() + ": " + e.getMessage());
                    continue;
                }
                ended += jdbc.update("UPDATE products SET status = 'ENDED', ebay_synced_at = now(), updated_at = now() WHERE id = ? AND status = 'LIVE'", g.getValue());
            }
        }

        // Parts an earlier sync ended (ebay_synced_at and updated_at set together, nothing changed since) whose listing
        // is on eBay after all, and which still have stock: a part sold on the website has none, so it stays ended.
        int relisted = 0;
        for (Map.Entry<String, Known> e : known.entrySet()) {
            if ("ENDED".equals(e.getValue().status()) && onEbay.containsKey(e.getKey())) {
                relisted += jdbc.update("""
                        UPDATE products SET status = 'LIVE', ebay_synced_at = now(), updated_at = now()
                        WHERE id = ? AND status = 'ENDED' AND stock > 0 AND ebay_synced_at = updated_at""", e.getValue().id());
            }
        }

        List<EbayCsv.Row> fresh = new ArrayList<>();
        int line = 0;
        for (EbayItemSource.Listing l : listings) {
            line++;
            if (!known.containsKey(l.itemNumber()) && l.title() != null) {
                // eBay's search doesn't give the quantity; most listings are one-off parts. Staff can change it in the admin.
                fresh.add(new EbayCsv.Row(line, l.itemNumber(), l.title(), null, 1, "FIXED_PRICE", l.price(), 0,
                        l.listedOn() != null ? l.listedOn() : LocalDate.now(), l.categoryId(), l.categoryName(),
                        l.condition() == null ? "" : l.condition()));
            }
        }
        int added = 0;
        if (!fresh.isEmpty()) {
            // Few threads: the live server has little memory, and a sync only meets a handful of new listings.
            EbayImportService.Summary s = importer.run("eBay sync", fresh,
                    new EbayImportService.Options(false, 0, true, false, false, 2));
            added = s.created();
            problems.addAll(s.problems());
        }
        // Live parts still without eBay photos (a download or upload failed earlier) get another try, a few per run.
        List<String> unphotographed = jdbc.queryForList("""
                SELECT p.ebay_item_id FROM products p
                WHERE p.status = 'LIVE' AND p.ebay_item_id IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM product_photos ph WHERE ph.product_id = p.id AND ph.source IN ('EBAY', 'UPLOAD'))
                ORDER BY p.added_on DESC LIMIT ?""", String.class, PHOTO_RETRIES_PER_RUN);
        List<EbayCsv.Row> retry = unphotographed.stream()
                .map(onEbay::get)
                .filter(java.util.Objects::nonNull)
                .map(l -> new EbayCsv.Row(0, l.itemNumber(), l.title(), null, 1, "FIXED_PRICE", l.price(), 0,
                        l.listedOn() != null ? l.listedOn() : LocalDate.now(), l.categoryId(), l.categoryName(),
                        l.condition() == null ? "" : l.condition()))
                .toList();
        if (!retry.isEmpty()) {
            // keepListings: only photos and item specifics; price, stock and title stay as they are.
            EbayImportService.Summary s = importer.run("eBay sync (photo retry)", retry,
                    new EbayImportService.Options(false, 0, true, false, true, 2));
            problems.addAll(s.problems());
        }
        if (catalog instanceof JdbcCatalogRepository db) {
            db.invalidate();
        }
        Result r = new Result(Instant.now(), listings.size(), added, repriced, ended, relisted, endingSkipped,
                List.copyOf(problems.size() > 50 ? problems.subList(0, 50) : problems));
        last = r;
        return r;
    }

    /** EBAY_SELLER, or the seller of one of the shop's eBay listings. */
    private String seller() throws IOException {
        if (seller == null) {
            for (String item : jdbc.queryForList(
                    "SELECT ebay_item_id FROM products WHERE ebay_item_id IS NOT NULL AND status = 'LIVE' ORDER BY updated_at DESC LIMIT 5", String.class)) {
                try {
                    seller = ebay.sellerOf(item).orElse(null);
                } catch (IOException e) {
                    log.debug("No seller for {}: {}", item, e.getMessage());
                }
                if (seller != null) {
                    break;
                }
            }
            if (seller == null) {
                throw new IOException("Couldn’t tell which eBay seller to follow. Set EBAY_SELLER.");
            }
        }
        return seller;
    }
}
