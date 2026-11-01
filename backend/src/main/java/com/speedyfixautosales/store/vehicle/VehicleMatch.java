package com.speedyfixautosales.store.vehicle;

import java.util.Locale;

/**
 * Whether a shopper's car (from their reg) is inside a compatibility row: the same make, the same model (DVSA's
 * "QASHQAI ACENTA" is a Qashqai), a year within the range, and, when the row names an engine, an engine of the same
 * size (within 60cc, as DVSA and eBay round differently) and the same fuel. Anything the reg didn't tell us (no year,
 * no engine size) isn't held against the match.
 */
public final class VehicleMatch {

    static final int CC_TOLERANCE = 60;

    private VehicleMatch() {
    }

    public static boolean matches(VehicleRange range, Vehicle car, Integer carCc, String carFuel) {
        if (!same(range.make(), car.make()) || !sameModel(range.model(), car.model())) {
            return false;
        }
        if (car.year() != null && (car.year() < range.yearFrom() || car.year() > range.yearTo())) {
            return false;
        }
        Integer cc = range.cc();
        if (cc != null && carCc != null && Math.abs(cc - carCc) > CC_TOLERANCE) {
            return false;
        }
        return range.fuel() == null || carFuel == null || fuelOf(carFuel).equals(fuelOf(range.fuel()));
    }

    private static boolean same(String a, String b) {
        return key(a).equals(key(b));
    }

    /** Models match when one starts with the other ("qashqai" and "qashqaiacenta"), ignoring spaces and punctuation. */
    private static boolean sameModel(String rangeModel, String carModel) {
        String r = key(rangeModel);
        String c = key(carModel);
        return !r.isEmpty() && !c.isEmpty() && (c.startsWith(r) || r.startsWith(c));
    }

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.UK).replace("&", "and").replaceAll("[^a-z0-9]", "");
    }

    /** DVSA says "HYBRID ELECTRIC (CLEAN)" or "PETROL"; eBay's variant says "Diesel SUV". Both come down to one word. */
    static String fuelOf(String fuel) {
        String f = fuel.toLowerCase(Locale.UK);
        if (f.contains("hybrid")) {
            return "hybrid";
        }
        if (f.contains("electric")) {
            return "electric";
        }
        if (f.contains("diesel")) {
            return "diesel";
        }
        if (f.contains("petrol") || f.contains("gasoline")) {
            return "petrol";
        }
        return f.trim();
    }
}
