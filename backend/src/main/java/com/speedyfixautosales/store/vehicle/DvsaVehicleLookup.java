package com.speedyfixautosales.store.vehicle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real plates through DVSA's MOT History API. Each result is cached for 30 days (unknown plates for a day) under a
 * hash of the plate, so repeat searches don't call DVSA and the plate itself isn't kept.
 *
 * <p>A DVSA vehicle's id encodes its make, model, year, engine size and fuel ("dvsa-…"), so a shopper's saved vehicle
 * still works after a restart without storing anything. Vehicles in the demo list (and their fitment) keep working
 * by id; demo plates no longer resolve, because they could be real cars.
 */
public class DvsaVehicleLookup implements VehicleLookup {

    private static final Logger log = LoggerFactory.getLogger(DvsaVehicleLookup.class);
    static final String ID_PREFIX = "dvsa-";
    private static final Duration FOUND_FOR = Duration.ofDays(30);
    private static final Duration NOT_FOUND_FOR = Duration.ofDays(1);

    private record Cached(Vehicle vehicle, Instant expires) {
    }

    private final DvsaClient dvsa;
    private final DemoVehicleLookup known;
    private final Map<String, Cached> byPlateHash = new ConcurrentHashMap<>();

    public DvsaVehicleLookup(DvsaClient dvsa, DemoVehicleLookup known) {
        this.dvsa = dvsa;
        this.known = known;
    }

    @Override
    public Optional<Vehicle> findByRegistration(String registration) {
        String key = hash(registration);
        Cached hit = byPlateHash.get(key);
        if (hit != null && hit.expires().isAfter(Instant.now())) {
            return Optional.ofNullable(hit.vehicle()).map(v -> withPlate(v, registration));
        }
        try {
            Optional<Vehicle> found = dvsa.find(registration).map(DvsaVehicleLookup::toVehicle);
            byPlateHash.put(key, new Cached(found.orElse(null), Instant.now().plus(found.isPresent() ? FOUND_FOR : NOT_FOUND_FOR)));
            return found.map(v -> withPlate(v, registration));
        } catch (IOException e) {
            // DVSA unreachable: treat as not found for now (not cached), so the shopper can try again or browse by category.
            log.warn("DVSA lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Vehicle> findById(String id) {
        if (id != null && id.startsWith(ID_PREFIX)) {
            return decode(id);
        }
        return known.findById(id);
    }

    /** Vehicles staff can pick by hand in the admin. DVSA vehicles are found by plate, so they aren't listed. */
    @Override
    public List<Vehicle> all() {
        return known.all();
    }

    @Override
    public boolean demo() {
        return false;
    }

    /** "NISSAN", "QASHQAI", 1197 cc, "PETROL", 2016 becomes Nissan Qashqai, engine "1.2L petrol", year 2016. */
    static Vehicle toVehicle(DvsaClient.DvsaVehicle d) {
        String make = titleCase(d.make());
        String model = titleCase(d.model());
        String fuel = d.fuelType() == null ? null : d.fuelType().toLowerCase(Locale.UK);
        return new Vehicle(encode(make, model, d.year(), d.engineSizeCc(), fuel), null, make, model,
                engine(d.engineSizeCc(), fuel), null, d.year());
    }

    static String engine(Integer cc, String fuel) {
        String size = cc == null || cc <= 0 ? null : String.format(Locale.UK, "%.1fL", Math.round(cc / 100.0) / 10.0);
        return size == null ? (fuel == null ? "Engine not known" : fuel) : fuel == null ? size : size + " " + fuel;
    }

    private static Vehicle withPlate(Vehicle v, String registration) {
        return new Vehicle(v.id(), registration, v.make(), v.model(), v.engine(), v.engineCode(), v.year());
    }

    /** The id carries make|model|year|cc|fuel (model cut to 30 characters), URL-safe, so it fits the 80-character id columns. */
    static String encode(String make, String model, Integer year, Integer cc, String fuel) {
        String m = model == null ? "" : model.length() > 30 ? model.substring(0, 30) : model;
        String raw = String.join("|", nz(make), m, year == null ? "" : year.toString(), cc == null ? "" : cc.toString(), nz(fuel));
        return ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Optional<Vehicle> decode(String id) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(id.substring(ID_PREFIX.length())), StandardCharsets.UTF_8);
            String[] f = raw.split("\\|", -1);
            if (f.length != 5 || f[0].isEmpty()) {
                return Optional.empty();
            }
            Integer year = f[2].isEmpty() ? null : Integer.valueOf(f[2]);
            Integer cc = f[3].isEmpty() ? null : Integer.valueOf(f[3]);
            String fuel = f[4].isEmpty() ? null : f[4];
            return Optional.of(new Vehicle(id, null, f[0], f[1], engine(cc, fuel), null, year));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.replace("|", " ");
    }

    /** Names that stay in capitals when DVSA's "LAND ROVER" becomes "Land Rover". */
    private static final Set<String> CAPITALS = Set.of("BMW", "MG", "DS", "VW", "GTI", "GT", "RS", "ST",
            "AMG", "SE", "SVR", "TDI", "TSI", "XE", "XF", "XJ", "XK", "CX", "MX", "HR", "CR", "RAV4", "DPF");

    /** Title case for DVSA's capitals, keeping known abbreviations and words with digits (CX-5, 3) as they are. */
    static String titleCase(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        StringBuilder out = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            String upper = word.toUpperCase(Locale.UK);
            boolean keep = CAPITALS.contains(upper.replaceAll("[^A-Z0-9]", "")) || word.matches(".*\\d.*");
            out.append(keep ? upper : upper.charAt(0) + upper.substring(1).toLowerCase(Locale.UK));
        }
        return out.toString();
    }

    private static String hash(String registration) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(registration.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
