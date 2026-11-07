package com.speedyfixautosales.store.catalog;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One spelling per brand, so the shop's brand filter doesn't list "Mercedes", "mercedes" and "Mercedes-Benz" as three
 * brands. eBay listings and staff spell brands many ways; every brand is saved through here.
 */
public final class Brands {

    /** Other spellings of a brand, by their lower-case form. Migration V23 tidied the brands saved before this. */
    private static final Map<String, String> SAME_AS = Map.ofEntries(
            Map.entry("mercedes", "Mercedes-Benz"),
            Map.entry("mercedes benz", "Mercedes-Benz"),
            Map.entry("mercedes-benz", "Mercedes-Benz"),
            Map.entry("vw", "Volkswagen"),
            Map.entry("range rover", "Land Rover"),
            Map.entry("landrover", "Land Rover"),
            Map.entry("fomoco", "Ford"),
            Map.entry("fomco", "Ford"),
            Map.entry("peigeot", "Peugeot"),
            Map.entry("bmw", "BMW"),
            Map.entry("bmw mini", "Mini"),
            Map.entry("mini", "Mini"),
            Map.entry("kia", "Kia"));

    private Brands() {
    }

    /** "mercedes" → "Mercedes-Benz", "land rover" → "Land Rover"; blank → null. Names already in capitals stay (BMW). */
    public static String canonical(String brand) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        String trimmed = brand.trim().replaceAll("\\s+", " ");
        String known = SAME_AS.get(trimmed.toLowerCase(Locale.UK));
        if (known != null) {
            return known;
        }
        // All lower or all upper case: "land rover" and "LAND ROVER" both become "Land Rover".
        boolean oneCase = trimmed.equals(trimmed.toLowerCase(Locale.UK)) || trimmed.equals(trimmed.toUpperCase(Locale.UK));
        return oneCase && trimmed.length() > 3 ? titleCase(trimmed) : trimmed;
    }

    private static String titleCase(String s) {
        return Arrays.stream(s.toLowerCase(Locale.UK).split(" "))
                .map(w -> Arrays.stream(w.split("-", -1))
                        .map(p -> p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1))
                        .collect(Collectors.joining("-")))
                .collect(Collectors.joining(" "));
    }
}
