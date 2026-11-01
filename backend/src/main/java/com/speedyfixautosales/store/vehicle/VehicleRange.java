package com.speedyfixautosales.store.vehicle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A row of a part's compatibility, picked by staff from eBay's UK vehicle catalogue: a make and model over a range of
 * years, for one engine or all of them ("Nissan Qashqai, 2014 to 2021, 1461cc 81KW 110HP K9K 646"). A shopper's car
 * fits when it's inside the range (see VehicleMatch).
 *
 * @param ebayEngine the engine exactly as eBay's catalogue names it, or null for all engines
 * @param fuel       petrol, diesel, hybrid or electric when known (from eBay's variant), else null
 */
public record VehicleRange(String id, String make, String model, int yearFrom, int yearTo, String ebayEngine, String fuel) {

    public static final String ID_PREFIX = "cat-";
    private static final Pattern CC = Pattern.compile("(\\d{3,5})\\s*cc", Pattern.CASE_INSENSITIVE);
    private static final Pattern HP = Pattern.compile("(\\d{2,4})\\s*HP", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("\\d+\\s*HP\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    public static VehicleRange of(String make, String model, int yearFrom, int yearTo, String ebayEngine, String fuel) {
        int from = Math.min(yearFrom, yearTo);
        int to = Math.max(yearFrom, yearTo);
        String engine = ebayEngine == null || ebayEngine.isBlank() ? null : ebayEngine.trim();
        return new VehicleRange(idFor(make, model, from, to, engine), make.trim(), model.trim(), from, to, engine, fuel);
    }

    /** The same make, model, years and engine always get the same id, so adding a row twice doesn't duplicate it. */
    static String idFor(String make, String model, int from, int to, String engine) {
        String raw = String.join("|", make.trim().toLowerCase(Locale.UK), model.trim().toLowerCase(Locale.UK),
                String.valueOf(from), String.valueOf(to), engine == null ? "" : engine.toLowerCase(Locale.UK));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return ID_PREFIX + HexFormat.of().formatHex(hash, 0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Engine size in cc, from eBay's "1461cc 81KW 110HP K9K 646". */
    public Integer cc() {
        if (ebayEngine == null) {
            return null;
        }
        Matcher m = CC.matcher(ebayEngine);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** The engine code eBay gives after the power ("K9K 646"), or null. */
    public String engineCode() {
        if (ebayEngine == null) {
            return null;
        }
        Matcher m = CODE.matcher(ebayEngine);
        if (!m.find()) {
            return null;
        }
        String code = m.group(1).split(";")[0].trim();
        return code.isEmpty() || code.length() > 20 ? null : code;
    }

    /** "1.5L diesel, 110HP", or "All engines". */
    public String engineLabel() {
        if (ebayEngine == null) {
            return fuel == null ? "All engines" : "All " + fuel + " engines";
        }
        Integer cc = cc();
        Matcher hp = HP.matcher(ebayEngine);
        String size = cc == null ? ebayEngine : String.format(Locale.UK, "%.1fL", Math.round(cc / 100.0) / 10.0);
        return size + (fuel == null ? "" : " " + fuel) + (hp.find() ? ", " + hp.group(1) + "HP" : "");
    }

    /** As a Vehicle for the admin and the product page: year is the first year, yearTo the last. */
    public Vehicle toVehicle() {
        return new Vehicle(id, null, make, model, engineLabel(), engineCode(), yearFrom, yearTo);
    }

    /** "2014 to 2021", or "2016" for a single year. */
    public String years() {
        return yearFrom == yearTo ? String.valueOf(yearFrom) : yearFrom + " to " + yearTo;
    }
}
