package com.speedyfixautosales.store.vehicle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * An eBay listing's compatibility table, as staff copy it from the listing page: tab-separated rows of make, model,
 * year, variant, type, chassis, engine and notes, 20 vehicles to a page. A copied header row sets the columns; without
 * one (a later page copied on its own) eBay UK's column order is assumed.
 */
public final class CompatibilityTable {

    /** eBay UK's columns, in the order the listing page shows them. */
    static final List<String> EBAY_COLUMNS = List.of("Make", "Model", "Year", "Variant", "Type", "Chassis", "Engine", "Notes");

    private CompatibilityTable() {
    }

    /** One row per vehicle, keyed by column name. Lines that aren't vehicles (headings, page numbers) are skipped. */
    public static List<Map<String, String>> parse(String text) {
        List<String> columns = EBAY_COLUMNS;
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\\R")) {
            if (!line.contains("\t")) {
                continue;
            }
            List<String> cells = Arrays.stream(line.split("\t", -1)).map(String::trim).toList();
            if (cells.stream().anyMatch("Make"::equalsIgnoreCase) && cells.stream().anyMatch("Model"::equalsIgnoreCase)) {
                columns = cells;
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(columns.size(), cells.size()); i++) {
                if (!cells.get(i).isEmpty()) {
                    row.put(columns.get(i), cells.get(i));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * eBay's table has a row per make, model, year, engine and variant (often hundreds); the shop keeps one row per
     * make, model and engine for each run of consecutive years, with the fuel where the variants agree on one.
     */
    public static List<VehicleRange> ranges(List<Map<String, String>> table) {
        record Key(String make, String model, String engine) {
        }
        Map<Key, TreeSet<Integer>> years = new LinkedHashMap<>();
        Map<Key, Set<String>> fuels = new LinkedHashMap<>();
        for (Map<String, String> row : table) {
            String make = cell(row, "Car Make", "Make");
            String model = cell(row, "Model", "Model");
            Integer year = year(cell(row, "Cars Year", "Year"));
            if (make == null || model == null || year == null) {
                continue;
            }
            Key k = new Key(make, model, cell(row, "Engine", "Engine"));
            years.computeIfAbsent(k, x -> new TreeSet<>()).add(year);
            fuels.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(fuel(row.get("Variant")));
        }
        List<VehicleRange> out = new ArrayList<>();
        years.forEach((k, ys) -> {
            Set<String> f = fuels.get(k);
            String fuel = f.size() == 1 ? f.iterator().next() : null;
            Integer from = null, prev = null;
            for (int y : ys) {
                if (from == null) {
                    from = y;
                } else if (y != prev + 1) {
                    out.add(VehicleRange.of(k.make(), k.model(), from, prev, k.engine(), fuel));
                    from = y;
                }
                prev = y;
            }
            out.add(VehicleRange.of(k.make(), k.model(), from, prev, k.engine(), fuel));
        });
        return out;
    }

    private static String cell(Map<String, String> row, String name, String fallback) {
        String v = row.getOrDefault(name, row.get(fallback));
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static Integer year(String s) {
        if (s == null || !s.trim().matches("\\d{4}")) {
            return null;
        }
        return Integer.parseInt(s.trim());
    }

    /** "Diesel SUV" is diesel; null when the variant doesn't say. */
    private static String fuel(String variant) {
        if (variant == null) {
            return null;
        }
        String v = variant.toLowerCase(Locale.UK);
        for (String f : List.of("hybrid", "electric", "diesel", "petrol")) {
            if (v.contains(f)) {
                return f;
            }
        }
        return null;
    }
}
