package com.speedyfixautosales.store.ebay;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls what it can out of an eBay title such as
 * "Nissan Qashqai J11 Auto Engine Control Unit 1.2L 237104ED9B 2014-2018": the make, the part number, the engine size
 * and the years. Used when eBay's item specifics are missing; specifics win when present.
 */
public final class TitleParser {

    public record Parsed(String make, String partNumber, String engineSize, Integer yearFrom, Integer yearTo) {
    }

    /** Longest names first, so "Land Rover" wins over "Rover". */
    private static final List<String> MAKES = List.of("Mercedes Benz", "Mercedes-Benz", "Land Rover", "Range Rover", "Alfa Romeo",
            "Aston Martin", "Mini", "Nissan", "Toyota", "Lexus", "Honda", "Mazda", "Mitsubishi", "Subaru", "Suzuki", "Isuzu",
            "Hyundai", "Kia", "Ford", "Vauxhall", "Opel", "Peugeot", "Citroen", "Renault", "Dacia", "Fiat", "Jeep", "Chrysler",
            "Dodge", "Jaguar", "Volvo", "Saab", "BMW", "Audi", "VW", "Volkswagen", "Seat", "Skoda", "Porsche", "Mercedes",
            "Infiniti", "Ssangyong", "Tesla", "Chevrolet", "Cadillac", "Maserati", "Bentley", "Smart", "Cupra", "DS", "MG");

    private static final Pattern YEARS = Pattern.compile("\\b((?:19|20)\\d{2})\\s*[-–]\\s*((?:19|20)\\d{2}|\\d{2})\\b");
    private static final Pattern SHORT_YEARS = Pattern.compile("(?:^|\\s)(\\d{2})\\s*-\\s*(\\d{2})(?:\\s|$)");
    private static final Pattern ENGINE_SIZE = Pattern.compile("\\b(\\d\\.\\d)\\s*L\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_RANGE_TOKEN = Pattern.compile("^\\d{2,4}-\\d{2,4}$");

    private TitleParser() {
    }

    public static Parsed parse(String title) {
        String t = title == null ? "" : title.trim();
        String make = MAKES.stream()
                .filter(m -> t.toLowerCase(Locale.UK).startsWith(m.toLowerCase(Locale.UK) + " "))
                .findFirst()
                .map(m -> m.equals("Mercedes-Benz") || m.equals("Mercedes") ? "Mercedes Benz" : m.equals("Volkswagen") ? "VW" : m)
                .orElse(null);

        Integer from = null;
        Integer to = null;
        Matcher y = YEARS.matcher(t);
        if (y.find()) {
            from = Integer.parseInt(y.group(1));
            to = fullYear(y.group(2), from);
        } else {
            Matcher s = SHORT_YEARS.matcher(t);
            if (s.find()) {
                // "09-16" means 2009-2016; two-digit years above 35 are the 1900s.
                int yy = Integer.parseInt(s.group(1));
                from = yy <= 35 ? 2000 + yy : 1900 + yy;
                to = fullYear(s.group(2), from);
            }
        }

        Matcher e = ENGINE_SIZE.matcher(t);
        String engineSize = e.find() ? e.group(1) + "L" : null;

        return new Parsed(make, partNumber(t), engineSize, from, to);
    }

    /**
     * The part number: the longest code-like word with at least one digit and 7+ letters and digits (hyphens allowed,
     * e.g. "35320-2B220"), that isn't a year range or an engine size. Model codes such as "J11" or "W177" are too short.
     */
    static String partNumber(String title) {
        String best = null;
        for (String raw : title.split("\\s+")) {
            String token = raw.replaceAll("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$", "");
            String compact = token.replace("-", "");
            boolean codeLike = compact.length() >= 7 && compact.matches("[A-Za-z0-9]+") && compact.matches(".*\\d.*")
                    && !YEAR_RANGE_TOKEN.matcher(token).matches() && !ENGINE_SIZE.matcher(token).matches();
            if (codeLike && (best == null || compact.length() >= best.replace("-", "").length())) {
                best = token.toUpperCase(Locale.UK);
            }
        }
        return best;
    }

    /** "2018" stays 2018; a two-digit "18" after 2014 becomes 2018. */
    private static int fullYear(String s, int from) {
        if (s.length() == 4) {
            return Integer.parseInt(s);
        }
        int century = from / 100 * 100;
        int year = century + Integer.parseInt(s);
        return year < from ? year + 100 : year;
    }
}
