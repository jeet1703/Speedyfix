package com.speedyfixautosales.store.order;

import java.util.Locale;
import java.util.Set;

/**
 * Where the shop delivers. Codes are ISO 3166 two-letter country codes ("GB", "IN"). Inside the UK VAT area (Great
 * Britain, Northern Ireland, which FedEx also calls GB, and the Isle of Man) prices include 20% VAT; everywhere else is
 * a zero-rated export: no UK VAT, and the buyer pays any import duty and tax on arrival.
 */
public final class Countries {

    public static final String UK = "GB";

    private static final Set<String> UK_VAT_AREA = Set.of("GB", "IM");
    /** Under UK sanctions or FedEx embargo: not offered at checkout. */
    private static final Set<String> NOT_DELIVERED = Set.of("RU", "BY", "IR", "KP", "SY", "CU");
    private static final Set<String> KNOWN = Set.of(Locale.getISOCountries());

    private Countries() {
    }

    /** "gb", " GB " or null become "GB" (null is the UK, for orders from before worldwide delivery). */
    public static String normalise(String country) {
        return country == null || country.isBlank() ? UK : country.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean delivered(String country) {
        String c = normalise(country);
        return KNOWN.contains(c) && !NOT_DELIVERED.contains(c);
    }

    /** True when the address pays UK VAT; false for a zero-rated export. */
    public static boolean ukVat(String country) {
        return UK_VAT_AREA.contains(normalise(country));
    }

    /** "India", "Germany": the country's English name. */
    public static String name(String country) {
        String c = normalise(country);
        String name = Locale.of("", c).getDisplayCountry(Locale.UK);
        return name.isBlank() ? c : name;
    }
}
