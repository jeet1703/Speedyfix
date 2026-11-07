package com.speedyfixautosales.store.vehicle;

import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.ebay.EbayItemSource;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * eBay UK's vehicle catalogue (makes, models, years, engines), for staff picking a part's compatibility. Answers are
 * kept for a day: the catalogue changes rarely, and the picker asks the same questions often.
 */
@Service
public class VehicleCatalogue {

    private static final Logger log = LoggerFactory.getLogger(VehicleCatalogue.class);
    private static final Duration KEEP = Duration.ofDays(1);
    static final int MAX_YEARS = 40;

    private record Cached(List<String> values, Instant until) {
    }

    private final EbayItemSource ebay;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public VehicleCatalogue(EbayItemSource ebay) {
        this.ebay = ebay;
    }

    public boolean enabled() {
        return ebay.enabled();
    }

    public List<String> makes() {
        return values("Car Make", Map.of());
    }

    public List<String> models(String make) {
        return values("Model", Map.of("Car Make", make));
    }

    /** Newest first. */
    public List<String> years(String make, String model) {
        return values("Cars Year", Map.of("Car Make", make, "Model", model)).stream()
                .sorted((a, b) -> b.compareTo(a)).toList();
    }

    /**
     * An engine to choose from: eBay's name, what staff read ("1.5L diesel, 110HP"), its fuel and engine code. The
     * fuel comes from eBay's variants in the first year the engine was fitted; null when they don't agree.
     */
    public record EngineChoice(String name, String label, String fuel, String engineCode) {
    }

    public List<EngineChoice> engineChoices(String make, String model, int yearFrom, int yearTo) {
        // A few eBay requests each, run side by side; every answer is kept for a day.
        return firstYears(make, model, yearFrom, yearTo).entrySet().parallelStream().map(e -> {
            String fuel = fuel(make, model, e.getValue(), e.getKey());
            VehicleRange r = VehicleRange.of(make, model, e.getValue(), e.getValue(), e.getKey(), fuel);
            return new EngineChoice(e.getKey(), r.engineLabel(), fuel, r.engineCode());
        }).sorted(Comparator.comparing(EngineChoice::label).thenComparing(EngineChoice::name)).toList();
    }

    /** Each engine fitted in the years, with the first year it was. */
    private Map<String, Integer> firstYears(String make, String model, int yearFrom, int yearTo) {
        Map<String, Integer> first = new TreeMap<>();
        int from = Math.min(yearFrom, yearTo);
        int to = Math.min(Math.max(yearFrom, yearTo), from + MAX_YEARS);
        for (int y = from; y <= to; y++) {
            for (String engine : values("Engine", Map.of("Car Make", make, "Model", model, "Cars Year", String.valueOf(y)))) {
                first.putIfAbsent(engine, y);
            }
        }
        return first;
    }

    /** Petrol, diesel, hybrid or electric when eBay's variants for this engine agree on one, else null. */
    public String fuel(String make, String model, int year, String engine) {
        if (engine == null) {
            return null;
        }
        List<String> fuels = values("Variant", Map.of("Car Make", make, "Model", model, "Cars Year", String.valueOf(year), "Engine", engine))
                .stream().map(VehicleMatch::fuelOf).map(f -> f.split(" ")[0]).distinct().toList();
        return fuels.size() == 1 && List.of("petrol", "diesel", "hybrid", "electric").contains(fuels.get(0)) ? fuels.get(0) : null;
    }

    private List<String> values(String property, Map<String, String> filter) {
        if (!enabled()) {
            throw new UnavailableException("The vehicle list isn’t set up on this server yet.");
        }
        // Filter order matters to eBay (make, then model, year, engine), so build it in that order.
        StringBuilder f = new StringBuilder();
        for (String k : List.of("Car Make", "Model", "Cars Year", "Engine")) {
            if (filter.containsKey(k)) {
                f.append(f.isEmpty() ? "" : ",").append(k).append(':').append(filter.get(k));
            }
        }
        String key = property + "?" + f;
        Cached hit = cache.get(key);
        if (hit != null && hit.until().isAfter(Instant.now())) {
            return hit.values();
        }
        try {
            List<String> v = ebay.compatibilityValues(property, f.toString());
            cache.put(key, new Cached(v, Instant.now().plus(KEEP)));
            return v;
        } catch (IOException e) {
            log.warn("eBay vehicle catalogue: {}", e.getMessage());
            throw new UnavailableException("The vehicle list didn’t load. Try again in a moment.");
        }
    }
}
