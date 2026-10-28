package com.speedyfixautosales.store.ebay;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads eBay Seller Hub's "All active listings" report (UTF-8, with a byte-order mark, quoted fields). */
final class EbayCsv {

    /** One listing. quantity is what's available now; sold is the eBay lifetime count. */
    record Row(int line, String itemNumber, String title, String sku, int quantity, String format, BigDecimal price,
               int sold, LocalDate startDate, String categoryId, String categoryName, String condition) {
    }

    // "Sep-27-25 03:09:03 PDT": only the date part is used.
    private static final DateTimeFormatter START_DATE = DateTimeFormatter.ofPattern("MMM-dd-yy", Locale.ENGLISH);

    private EbayCsv() {
    }

    static List<Row> read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> records = parse(text);
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> col = new HashMap<>();
        List<String> header = records.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim(), i);
        }
        for (String required : List.of("Item number", "Title", "Available quantity", "Format", "Current price", "Condition",
                "eBay category 1 name", "eBay category 1 number")) {
            if (!col.containsKey(required)) {
                throw new IOException("This doesn’t look like an eBay active listings report: no \"" + required + "\" column.");
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> f = records.get(r);
            if (f.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(new Row(r + 1, get(f, col, "Item number"), get(f, col, "Title").replaceAll("\\s+", " ").trim(),
                    get(f, col, "Custom label (SKU)"), parseInt(get(f, col, "Available quantity")), get(f, col, "Format"),
                    parsePrice(get(f, col, "Current price")), parseInt(get(f, col, "Sold quantity")),
                    parseDate(get(f, col, "Start date")), get(f, col, "eBay category 1 number"),
                    get(f, col, "eBay category 1 name"), get(f, col, "Condition")));
        }
        return rows;
    }

    private static String get(List<String> fields, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i >= fields.size() ? "" : fields.get(i).trim();
    }

    private static int parseInt(String s) {
        try {
            return s.isBlank() ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal parsePrice(String s) {
        try {
            return s.isBlank() ? null : new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        try {
            return s.length() < 9 ? null : LocalDate.parse(s.substring(0, 9), START_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** RFC 4180 CSV: commas inside quotes, doubled quotes ("") for a quote, line breaks inside quoted fields. */
    static List<List<String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                field.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
