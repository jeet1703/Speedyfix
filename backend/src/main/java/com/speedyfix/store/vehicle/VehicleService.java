package com.speedyfix.store.vehicle;

import com.speedyfix.store.common.BadRequestException;
import com.speedyfix.store.common.NotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
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

    public VehicleService(VehicleLookup lookup) {
        this.lookup = lookup;
    }

    public Vehicle lookUp(String rawRegistration) {
        String reg = normalise(rawRegistration);
        if (reg.isEmpty()) {
            throw new BadRequestException("Enter your registration number.");
        }
        if (UK_FORMATS.stream().noneMatch(p -> p.matcher(reg).matches())) {
            throw new BadRequestException("Enter a valid UK registration, like AB12 CDE.");
        }
        return lookup.findByRegistration(reg).orElseThrow(() -> new NotFoundException(
                "We couldn’t find " + display(reg) + ". In this demo, try AB12 CDE, VX16 ABC or JG17 XEE."));
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
        return id == null || id.isBlank() ? Optional.empty() : lookup.findById(id);
    }

    public List<Vehicle> all() {
        return lookup.all();
    }

    static String normalise(String registration) {
        return registration == null ? "" : registration.toUpperCase(Locale.UK).replaceAll("[^A-Z0-9]", "");
    }

    /** Formats a current-style plate as it appears on the car: AB12 CDE. */
    static String display(String reg) {
        return reg.length() == 7 && reg.matches("^[A-Z]{2}[0-9]{2}[A-Z]{3}$") ? reg.substring(0, 4) + " " + reg.substring(4) : reg;
    }
}
