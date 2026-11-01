package com.speedyfixautosales.store.vehicle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Compatibility rows in the vehicles table (variant holds eBay's engine name). They're few and read on every reg
 * check, so all of them are kept in memory and reloaded when one is added.
 */
@Component
@Profile("postgres")
public class JdbcVehicleRanges implements VehicleRanges {

    private final JdbcTemplate jdbc;
    private volatile Map<String, VehicleRange> cache;

    public JdbcVehicleRanges(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public synchronized VehicleRange save(VehicleRange r) {
        jdbc.update("""
                INSERT INTO vehicles (id, make, model, variant, year_from, year_to, engine, engine_code, fuel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING""",
                r.id(), cut(r.make(), 60), cut(r.model(), 100), cut(r.ebayEngine(), 100), r.yearFrom(), r.yearTo(),
                cut(r.engineLabel(), 100), cut(r.engineCode(), 20), cut(r.fuel(), 20));
        cache = null;
        return find(r.id()).orElse(r);
    }

    @Override
    public Optional<VehicleRange> find(String id) {
        return Optional.ofNullable(id == null ? null : ranges().get(id));
    }

    @Override
    public List<VehicleRange> all() {
        return List.copyOf(ranges().values());
    }

    private Map<String, VehicleRange> ranges() {
        Map<String, VehicleRange> c = cache;
        if (c == null) {
            Map<String, VehicleRange> loaded = new ConcurrentHashMap<>();
            jdbc.query("SELECT id, make, model, variant, year_from, year_to, fuel FROM vehicles WHERE id LIKE ?",
                    rs -> {
                        loaded.put(rs.getString("id"), new VehicleRange(rs.getString("id"), rs.getString("make"), rs.getString("model"),
                                rs.getInt("year_from"), rs.getInt("year_to"), rs.getString("variant"), rs.getString("fuel")));
                    }, VehicleRange.ID_PREFIX + "%");
            cache = c = loaded;
        }
        return c;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
