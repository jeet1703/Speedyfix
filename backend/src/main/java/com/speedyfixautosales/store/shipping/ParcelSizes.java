package com.speedyfixautosales.store.shipping;

import com.speedyfixautosales.store.catalog.Product;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * How big and heavy a part's box is, for FedEx's rates. eBay listings don't carry weights, so each category has a
 * typical packed box below; staff can set a part's own weight on its listing, kept in products.weight_kg with the
 * "postgres" profile (in memory without it). The sizes are estimates to check against real parcels.
 */
@Component
public class ParcelSizes {

    record Box(BigDecimal weightKg, int lengthCm, int widthCm, int heightCm) {
        static Box of(String kg, int l, int w, int h) {
            return new Box(new BigDecimal(kg), l, w, h);
        }
    }

    static final Box DEFAULT = Box.of("5", 50, 40, 30);

    /** Typical packed box per category, in kg and cm. Engines and gearboxes normally go by pallet instead. */
    static final Map<String, Box> BY_CATEGORY = Map.ofEntries(
            Map.entry("engines", Box.of("150", 100, 80, 80)),
            Map.entry("bare-engines", Box.of("100", 90, 70, 70)),
            Map.entry("short-motors", Box.of("70", 80, 60, 60)),
            Map.entry("cylinder-blocks", Box.of("50", 70, 50, 50)),
            Map.entry("crankshafts", Box.of("20", 80, 30, 30)),
            Map.entry("camshafts", Box.of("5", 70, 20, 15)),
            Map.entry("sumps-pans", Box.of("6", 60, 40, 25)),
            Map.entry("oil-pumps", Box.of("3", 40, 30, 20)),
            Map.entry("ac-compressors", Box.of("8", 40, 30, 30)),
            Map.entry("fuel-injectors", Box.of("1", 30, 20, 15)),
            Map.entry("manifolds", Box.of("6", 70, 40, 30)),
            Map.entry("egr-valves", Box.of("4", 50, 40, 30)),
            Map.entry("audio-media", Box.of("3", 50, 40, 30)),
            Map.entry("wiring-looms", Box.of("4", 50, 40, 30)),
            Map.entry("gearboxes", Box.of("60", 80, 60, 50)),
            Map.entry("cylinder-heads", Box.of("30", 80, 40, 30)),
            Map.entry("body-panels", Box.of("15", 150, 80, 20)),
            Map.entry("doors", Box.of("25", 130, 100, 25)),
            Map.entry("turbochargers", Box.of("12", 50, 40, 40)),
            Map.entry("ecus-electronics", Box.of("2", 35, 25, 15)),
            Map.entry("engine-parts", Box.of("8", 50, 40, 30)),
            Map.entry("starters-alternators", Box.of("7", 40, 30, 30)),
            Map.entry("fuel-injection", Box.of("3", 40, 30, 20)),
            Map.entry("cooling-ac", Box.of("8", 80, 60, 20)),
            Map.entry("lights-mirrors", Box.of("5", 70, 50, 40)),
            Map.entry("drivetrain", Box.of("15", 120, 30, 30)),
            Map.entry("interior", Box.of("8", 80, 60, 40)),
            Map.entry("egr-emissions", Box.of("5", 50, 40, 30)),
            Map.entry("air-intake", Box.of("4", 60, 40, 30)),
            Map.entry("accessories", Box.of("3", 40, 30, 20)),
            Map.entry("steering-suspension", Box.of("12", 100, 40, 30)),
            // Coils and leads: at 40x30x20 a 2 kg box was billed as 4.8 kg by size.
            Map.entry("ignition", Box.of("1.5", 30, 20, 15)),
            Map.entry("instruments-infotainment", Box.of("3", 50, 40, 30)),
            Map.entry("engine-mounts", Box.of("4", 40, 30, 20)),
            Map.entry("electrics-sensors", Box.of("1", 30, 20, 15)),
            Map.entry("brakes", Box.of("10", 50, 40, 30)),
            Map.entry("rebuild-kits", Box.of("6", 50, 40, 30)));

    /** Null without the database. */
    private final JdbcTemplate jdbc;
    private final Map<String, BigDecimal> memory = new ConcurrentHashMap<>();

    public ParcelSizes(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc.getIfAvailable();
    }

    /** The weight staff set on the part, if any. */
    public Optional<BigDecimal> weight(String productId) {
        if (jdbc == null) {
            return Optional.ofNullable(memory.get(productId));
        }
        return jdbc.query("SELECT weight_kg FROM products WHERE id = ? AND weight_kg IS NOT NULL",
                (rs, row) -> rs.getBigDecimal(1), productId).stream().findFirst();
    }

    /** Sets (or, with null, clears) the part's own weight. */
    public void setWeight(String productId, BigDecimal weightKg) {
        if (jdbc != null) {
            jdbc.update("UPDATE products SET weight_kg = ? WHERE id = ?", weightKg, productId);
        } else if (weightKg == null) {
            memory.remove(productId);
        } else {
            memory.put(productId, weightKg);
        }
    }

    /** The category's typical packed weight, used when a part has none of its own. */
    public static BigDecimal typicalWeight(String categoryId) {
        return BY_CATEGORY.getOrDefault(categoryId, DEFAULT).weightKg();
    }

    /** One of the part, boxed: its category's box, with the part's own weight when staff set one. */
    public FedExClient.Parcel parcel(Product product) {
        Box box = BY_CATEGORY.getOrDefault(product.categoryId(), DEFAULT);
        BigDecimal kg = weight(product.id()).orElse(box.weightKg());
        return new FedExClient.Parcel(kg, box.lengthCm(), box.widthCm(), box.heightCm());
    }
}
