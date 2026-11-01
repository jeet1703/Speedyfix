package com.speedyfixautosales.store.vehicle;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.NotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VehicleService {

    /** Current (AB12CDE), prefix (A123BCD), suffix (ABC123D) and dateless (ABC1234 / 1234ABC) UK formats. */
    private static final List<Pattern> UK_FORMATS = List.of(
            Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z]{3}$"),
            Pattern.compile("^[A-Z][0-9]{1,3}[A-Z]{3}$"),
            Pattern.compile("^[A-Z]{3}[0-9]{1,3}[A-Z]$"),
            Pattern.compile("^[A-Z]{1,3}[0-9]{1,4}$"),
            Pattern.compile("^[0-9]{1,4}[A-Z]{1,3}$"));

    private final VehicleLookup lookup;
    private final VehicleRanges ranges;

    /** Without the database: compatibility rows are kept in memory (tests, the demo catalogue). */
    public VehicleService(VehicleLookup lookup) {
        this(lookup, new InMemoryVehicleRanges());
    }

    @Autowired
    public VehicleService(VehicleLookup lookup, VehicleRanges ranges) {
        this.lookup = lookup;
        this.ranges = ranges;
    }

    /** Adds a compatibility row picked from eBay's vehicle catalogue (the same row twice is kept once). */
    public Vehicle addRange(VehicleRange range) {
        return ranges.save(range).toVehicle();
    }

    /**
     * Whether a shopper's car is one of a part's vehicles: the same vehicle, or inside one of its compatibility rows
     * (make, model, years and engine; see VehicleMatch).
     */
    public boolean fits(List<String> partVehicleIds, Vehicle car) {
        if (partVehicleIds.contains(car.id())) {
            return true;
        }
        DvsaVehicleLookup.Facts facts = DvsaVehicleLookup.facts(car.id()).orElse(new DvsaVehicleLookup.Facts(null, null));
        for (String id : partVehicleIds) {
            if (id.startsWith(VehicleRange.ID_PREFIX)) {
                Optional<VehicleRange> range = ranges.find(id);
                if (range.isPresent() && VehicleMatch.matches(range.get(), car, facts.cc(), facts.fuel())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "Nissan Qashqai, 2014 to 2021, 1.5L diesel, 110HP (K9K)" for a compatibility row; "Nissan Navara D40, 2.5 dCi diesel" otherwise. */
    public static String describe(Vehicle v) {
        String years = v.yearTo() != null && v.year() != null
                ? (v.year().equals(v.yearTo()) ? ", " + v.year() : ", " + v.year() + " to " + v.yearTo()) : "";
        return v.displayName() + years + ", " + v.engine() + (v.engineCode() != null && v.yearTo() != null ? " (" + v.engineCode() + ")" : "");
    }

    public Vehicle lookUp(String rawRegistration) {
        String reg = normalise(rawRegistration);
        if (reg.isEmpty()) {
            throw new BadRequestException("Enter your registration number.");
        }
        if (UK_FORMATS.stream().noneMatch(p -> p.matcher(reg).matches())) {
            throw new BadRequestException("Enter a valid UK registration, like AB12 CDE.");
        }
        return lookup.findByRegistration(reg).orElseThrow(() -> new NotFoundException(lookup.demo()
                ? "We couldn’t find " + display(reg) + ". In this demo, try AB12 CDE, VX16 ABC or JG17 XEE."
                : "We couldn’t find " + display(reg) + ". Check the registration, or browse parts by category."));
    }

    /** Like lookUp, but a valid plate the data source doesn't know gives an empty result instead of an error. */
    public Optional<Vehicle> identify(String rawRegistration) {
        try {
            return Optional.of(lookUp(rawRegistration));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    /** Keeps only the first four characters, so a stored plate can't identify the car: AB12 CDE becomes AB12 ***. */
    public static String mask(String rawRegistration) {
        String reg = normalise(rawRegistration);
        return reg.length() <= 4 ? "****" : reg.substring(0, 4) + " " + "*".repeat(reg.length() - 4);
    }

    public Optional<Vehicle> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        if (id.startsWith(VehicleRange.ID_PREFIX)) {
            return ranges.find(id).map(VehicleRange::toVehicle);
        }
        // A DVSA vehicle's id carries its details, so it reads back without DVSA (or its keys) being there.
        if (id.startsWith(DvsaVehicleLookup.ID_PREFIX)) {
            return DvsaVehicleLookup.decode(id);
        }
        return lookup.findById(id);
    }

    /** The demo vehicles and every compatibility row staff have added. */
    public List<Vehicle> all() {
        return Stream.concat(lookup.all().stream(), ranges.all().stream().map(VehicleRange::toVehicle)).toList();
    }

    /** True while plates come from the sample list rather than DVSA, so pages can suggest the demo plates. */
    public boolean demoPlates() {
        return lookup.demo();
    }

    static String normalise(String registration) {
        return registration == null ? "" : registration.toUpperCase(Locale.UK).replaceAll("[^A-Z0-9]", "");
    }

    /** Formats a current-style plate as it appears on the car: AB12 CDE. */
    static String display(String reg) {
        return reg.length() == 7 && reg.matches("^[A-Z]{2}[0-9]{2}[A-Z]{3}$") ? reg.substring(0, 4) + " " + reg.substring(4) : reg;
    }
}
