package com.speedyfixautosales.store.vehicle;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Shoppers' plate lookups, for the admin dashboard. Stores only when, which vehicle (if recognised)
 * and whether any part fitted: never the plate itself. In the lookup_log table with the "postgres" profile,
 * otherwise in memory. Entries older than 40 days are dropped.
 */
@Component
public class LookupLog {

    static final Duration KEEP = Duration.ofDays(40);
    static final int MAX_ENTRIES = 50_000;

    public record Entry(Instant at, String vehicleId, boolean matched) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    /** Null without the database. */
    private final JdbcTemplate jdbc;

    public LookupLog(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc.getIfAvailable();
    }

    /** @param vehicleId null when the plate wasn't recognised */
    public synchronized void record(String vehicleId, boolean matched) {
        if (jdbc != null) {
            jdbc.update("INSERT INTO lookup_log (vehicle_id, matched) VALUES (?, ?)", vehicleId, matched);
            return;
        }
        Instant now = Instant.now();
        entries.addLast(new Entry(now, vehicleId, matched));
        while (entries.size() > MAX_ENTRIES || (!entries.isEmpty() && entries.peekFirst().at().isBefore(now.minus(KEEP)))) {
            entries.removeFirst();
        }
    }

    @Scheduled(fixedDelayString = "PT6H")
    void removeOld() {
        if (jdbc != null) {
            jdbc.update("DELETE FROM lookup_log WHERE looked_up < ?", Timestamp.from(Instant.now().minus(KEEP)));
        }
    }

    public synchronized long countSince(Instant since) {
        if (jdbc != null) {
            return jdbc.queryForObject("SELECT count(*) FROM lookup_log WHERE looked_up >= ?", Long.class, Timestamp.from(since));
        }
        return entries.stream().filter(e -> !e.at().isBefore(since)).count();
    }

    public synchronized long noMatchSince(Instant since) {
        if (jdbc != null) {
            return jdbc.queryForObject("SELECT count(*) FROM lookup_log WHERE NOT matched AND looked_up >= ?", Long.class,
                    Timestamp.from(since));
        }
        return entries.stream().filter(e -> !e.matched() && !e.at().isBefore(since)).count();
    }

    /** Vehicle id to number of lookups with no fitting stock, most searched first. Unrecognised plates use the key null. */
    public synchronized Map<String, Long> noMatchByVehicle(Instant since) {
        Map<String, Long> sorted = new LinkedHashMap<>();
        if (jdbc != null) {
            jdbc.query("""
                    SELECT vehicle_id, count(*) AS n FROM lookup_log WHERE NOT matched AND looked_up >= ?
                    GROUP BY vehicle_id ORDER BY n DESC""",
                    rs -> {
                        sorted.put(rs.getString("vehicle_id"), rs.getLong("n"));
                    }, Timestamp.from(since));
            return sorted;
        }
        Map<String, Long> counts = entries.stream()
                .filter(e -> !e.matched() && !e.at().isBefore(since))
                .collect(Collectors.groupingBy(e -> e.vehicleId() == null ? "" : e.vehicleId(), Collectors.counting()));
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey().isEmpty() ? null : e.getKey(), e.getValue()));
        return sorted;
    }
}
