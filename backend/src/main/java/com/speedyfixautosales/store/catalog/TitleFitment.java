package com.speedyfixautosales.store.catalog;

import com.speedyfixautosales.store.ebay.TitleParser;
import com.speedyfixautosales.store.vehicle.Vehicle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Judges fit from a listing's title when nobody has confirmed the vehicle: "Nissan Qashqai J11 ECU 1.2L 237104ED9B
 * 2014-2018" likely fits a 2016 1.2 Nissan Qashqai, and doesn't fit a Ford. Titles are parsed once and remembered.
 */
final class TitleFitment {

    /** How the title compares with the vehicle. */
    enum Verdict {
        LIKELY, NO, UNKNOWN
    }

    /** eBay titles and DVSA don't always spell a make the same way. */
    private static final Map<String, List<String>> MAKE_NAMES = Map.of(
            "MERCEDES-BENZ", List.of("MERCEDES", "MERCEDES-BENZ", "MERCEDES BENZ"),
            "MERCEDES BENZ", List.of("MERCEDES", "MERCEDES-BENZ", "MERCEDES BENZ"),
            "VOLKSWAGEN", List.of("VOLKSWAGEN", "VW"),
            "VW", List.of("VOLKSWAGEN", "VW"),
            "LAND ROVER", List.of("LAND ROVER", "RANGE ROVER"),
            "VAUXHALL", List.of("VAUXHALL", "OPEL"),
            "MINI", List.of("MINI", "BMW MINI"));

    private static final Pattern SIZE = Pattern.compile("(\\d\\.\\d)");

    private final Map<String, TitleParser.Parsed> parsed = new ConcurrentHashMap<>();

    Verdict judge(Product p, Vehicle v) {
        if (v.make() == null || v.model() == null) {
            return Verdict.UNKNOWN;
        }
        String title = " " + p.name().toUpperCase(Locale.UK).replaceAll("[^A-Z0-9.]+", " ") + " ";
        TitleParser.Parsed t = parsed.computeIfAbsent(p.id() + "|" + p.name(), k -> TitleParser.parse(p.name()));

        String make = v.make().toUpperCase(Locale.UK);
        boolean makeNamed = MAKE_NAMES.getOrDefault(make, List.of(make)).stream()
                .anyMatch(name -> title.contains(" " + name.replaceAll("[^A-Z0-9.]+", " ") + " "));
        if (!makeNamed) {
            // A title that starts with another make is for that make; anything else we can't tell.
            return t.make() != null ? Verdict.NO : Verdict.UNKNOWN;
        }
        if (!title.contains(" " + modelWord(v.model()) + " ")) {
            return Verdict.NO;
        }
        if (v.year() != null && t.yearFrom() != null && t.yearTo() != null && (v.year() < t.yearFrom() || v.year() > t.yearTo())) {
            return Verdict.NO;
        }
        String vehicleSize = size(v.engine());
        String titleSize = t.engineSize() == null ? null : size(t.engineSize());
        if (vehicleSize != null && titleSize != null && !vehicleSize.equals(titleSize)) {
            return Verdict.NO;
        }
        return Verdict.LIKELY;
    }

    /** The model's main word: "QASHQAI" from "Qashqai Acenta", "3 SERIES" from "3 Series", "CX-5" as "CX 5". */
    static String modelWord(String model) {
        String[] words = model.toUpperCase(Locale.UK).replaceAll("[^A-Z0-9.]+", " ").trim().split(" ");
        return words.length > 1 && words[0].length() <= 2 ? words[0] + " " + words[1] : words[0];
    }

    private static String size(String engine) {
        if (engine == null) {
            return null;
        }
        Matcher m = SIZE.matcher(engine);
        return m.find() ? m.group(1) : null;
    }
}
