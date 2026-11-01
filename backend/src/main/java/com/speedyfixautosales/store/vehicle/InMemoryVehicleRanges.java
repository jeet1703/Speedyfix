package com.speedyfixautosales.store.vehicle;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Compatibility rows in memory, for running without a database. */
@Component
@Profile("!postgres")
public class InMemoryVehicleRanges implements VehicleRanges {

    private final Map<String, VehicleRange> ranges = new ConcurrentHashMap<>();

    @Override
    public VehicleRange save(VehicleRange range) {
        return ranges.computeIfAbsent(range.id(), id -> range);
    }

    @Override
    public Optional<VehicleRange> find(String id) {
        return Optional.ofNullable(id == null ? null : ranges.get(id));
    }

    @Override
    public List<VehicleRange> all() {
        return ranges.values().stream()
                .sorted(Comparator.comparing(VehicleRange::make).thenComparing(VehicleRange::model).thenComparing(VehicleRange::yearFrom))
                .toList();
    }
}
