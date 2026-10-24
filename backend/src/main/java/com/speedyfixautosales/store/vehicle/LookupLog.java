package com.speedyfixautosales.store.vehicle;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Shoppers' plate lookups, for the admin dashboard. Stores only when, which vehicle (if recognised)
 * and whether any part fitted: never the plate itself.
 */
@Component
public class LookupLog {

    static final Duration KEEP = Duration.ofDays(40);
    static final int MAX_ENTRIES = 50_000;

    public record Entry(Instant at, String vehicleId, boolean matched) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    /** @param vehicleId null when the plate wasn't recognised */
    public synchronized void record(String vehicleId, boolean matched) {
        Instant now = Instant.now();
        entries.addLast(new Entry(now, vehicleId, matched));
        while (entries.size() > MAX_ENTRIES || (!entries.isEmpty() && entries.peekFirst().at().isBefore(now.minus(KEEP)))) {
            entries.removeFirst();
        }
    }

    public synchronized long countSince(Instant since) {
        return entries.stream().filter(e -> !e.at().isBefore(since)).count();
    }

    public synchronized long noMatchSince(Instant since) {
        return entries.stream().filter(e -> !e.matched() && !e.at().isBefore(since)).count();
    }

    /** Vehicle id to number of lookups with no fitting stock, most searched first. Unrecognised plates use the key null. */
    public synchronized Map<String, Long> noMatchByVehicle(Instant since) {
        Map<String, Long> counts = entries.stream()
                .filter(e -> !e.matched() && !e.at().isBefore(since))
                .collect(Collectors.groupingBy(e -> e.vehicleId() == null ? "" : e.vehicleId(), Collectors.counting()));
        Map<String, Long> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey().isEmpty() ? null : e.getKey(), e.getValue()));
        return sorted;
    }
}
