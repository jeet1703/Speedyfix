package com.speedyfixautosales.store.shipping;

import com.speedyfixautosales.store.basket.BasketService;
import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.catalog.Product;
import com.speedyfixautosales.store.catalog.Shipping;
import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.order.Countries;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The delivery choices for a basket going to a postcode. With FedEx set up (FedExClient), everything is priced by
 * FedEx, heavy parts (engines, gearboxes) included: FedEx quotes those as FedEx Freight. The price is FedEx's charge to
 * the shop plus any markup (fedex.markup-percent, fedex.handling-fee) and VAT. In the UK the cheapest service is free
 * when every part weighs up to delivery.free-up-to-kg (5 kg), however many there are, and no service costs more than
 * delivery.uk-max-price (£100) when one is heavier; abroad, FedEx's price applies. Manual delivery (a quote by hand after payment) is
 * always offered too, and is the only choice when FedEx can't price the order, so checkout never stops. Without
 * FedEx set up, the flat prices apply (BasketService). Collection from the store is offered free to UK addresses (a part
 * collected in Uxbridge is a UK sale, never a VAT-free export).
 */
@Service
public class DeliveryQuotes {

    private static final Logger log = LoggerFactory.getLogger(DeliveryQuotes.class);
    private static final Duration CACHE_FOR = Duration.ofMinutes(30);
    private static final int MAX_CACHED = 1_000;
    /** More boxes than this and the order is quoted by hand (manual delivery only). */
    static final int MAX_PARCELS = 20;
    static final String FEDEX = "FEDEX";
    /** The basket's delivery before a postcode is known, while FedEx prices it. */
    public static final String AT_CHECKOUT = "QUOTE";
    /**
     * The shopper doesn't want FedEx: they pay for the parts only, then contact the shop for a quote from another
     * delivery partner, paid separately.
     */
    public static final String MANUAL = "MANUAL";
    static final Option MANUAL_OPTION = new Option(MANUAL, MANUAL, "Manual delivery: contact us for a quote after payment",
            BigDecimal.ZERO.setScale(2), null);

    /** The shopper collects the parts from the store in Uxbridge: nothing is sent, and there's nothing to pay for it. */
    public static final String COLLECT = "COLLECT";
    static final Option COLLECT_OPTION = new Option(COLLECT, COLLECT, "Collect from our store in Uxbridge",
            BigDecimal.ZERO.setScale(2), null);

    /** True when the shopper collects the order from the store. */
    public static boolean collect(BasketView.Delivery delivery) {
        return delivery != null && COLLECT.equals(delivery.method());
    }

    /** True when the order's delivery is quoted by hand after payment, so it isn't in the order total. */
    public static boolean manual(BasketView.Delivery delivery) {
        return delivery != null && MANUAL.equals(delivery.method());
    }

    /** One choice at checkout. id is what the shopper sends back to choose it; arrives is a date (yyyy-MM-dd), or null. */
    public record Option(String id, String method, String label, BigDecimal price, String arrives) {
        BasketView.Delivery delivery() {
            return new BasketView.Delivery(method, label, price);
        }
    }

    private record Cached(List<FedExClient.Rate> rates, Instant at) {
    }

    private final FedExClient fedex;
    private final ParcelSizes sizes;
    private final CatalogService catalog;
    private final BigDecimal markupPercent;
    private final BigDecimal handlingFee;
    private final boolean addVat;
    private final BigDecimal freeUpToKg;
    private final BigDecimal ukMaxPrice;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public DeliveryQuotes(FedExClient fedex, ParcelSizes sizes, CatalogService catalog,
                          @Value("${fedex.markup-percent:0}") BigDecimal markupPercent,
                          @Value("${fedex.handling-fee:0}") BigDecimal handlingFee,
                          @Value("${fedex.add-vat:true}") boolean addVat,
                          @Value("${delivery.free-up-to-kg:5}") BigDecimal freeUpToKg,
                          @Value("${delivery.uk-max-price:100}") BigDecimal ukMaxPrice) {
        this.fedex = fedex;
        this.sizes = sizes;
        this.catalog = catalog;
        this.markupPercent = markupPercent;
        this.handlingFee = handlingFee;
        this.addVat = addVat;
        this.freeUpToKg = freeUpToKg;
        this.ukMaxPrice = ukMaxPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /** True when parcels are priced by FedEx from the postcode at checkout. */
    public boolean live() {
        return fedex.enabled();
    }

    /**
     * The basket as the shop shows it before checkout: with FedEx on, parcel delivery is shown as worked out at
     * checkout (free or not depends on the weight and where it's going), rather than a price that would change.
     */
    public BasketView beforeCheckout(BasketView basket) {
        String method = basket.delivery().method();
        boolean flatPriced = "STANDARD".equals(method) || "PALLET".equals(method);
        if (!live() || !flatPriced) {
            return basket;
        }
        return BasketService.withDelivery(basket, new BasketView.Delivery(AT_CHECKOUT, "Delivery, worked out at checkout", BigDecimal.ZERO.setScale(2)));
    }

    /** The choices for the basket to a UK postcode. */
    public List<Option> options(BasketView basket, String postcode) {
        return options(basket, FedExClient.Destination.uk(postcode));
    }

    /**
     * The choices for the basket to the destination, the cheapest first, then manual delivery, then (in the UK)
     * collection from the store. Abroad, prices have no UK VAT (a zero-rated export) and nothing is free; without FedEx, delivery abroad
     * is quoted by hand.
     */
    public List<Option> options(BasketView basket, FedExClient.Destination to) {
        if (basket.lines().isEmpty()) {
            return List.of();
        }
        boolean export = !Countries.ukVat(to.country());
        BasketView.Delivery flat = basket.delivery();
        if (!live()) {
            return export ? List.of(MANUAL_OPTION)
                    : List.of(new Option(flat.method(), flat.method(), flat.label(), flat.price(), null), COLLECT_OPTION);
        }
        List<Option> options = new ArrayList<>(fedexOptions(basket, to, "PALLET".equals(flat.method()), export));
        options.add(MANUAL_OPTION);
        if (!export) {
            options.add(COLLECT_OPTION);
        }
        return List.copyOf(options);
    }

    /**
     * FedEx won't quote freight and parcels in one shipment, so with an engine or gearbox in the basket the small parts
     * travel on its pallet: their weight goes onto the first pallet, and they aren't sent as boxes of their own.
     */
    private List<FedExClient.Parcel> onThePallet(BasketView basket, List<FedExClient.Parcel> all) {
        List<FedExClient.Parcel> pallets = new ArrayList<>();
        BigDecimal loose = BigDecimal.ZERO;
        int i = 0;
        for (BasketView.Line line : basket.lines()) {
            boolean pallet = catalog.findAny(line.productId()).shipping() == Shipping.PALLET;
            for (int q = 0; q < line.quantity(); q++, i++) {
                if (pallet) {
                    pallets.add(all.get(i));
                } else {
                    loose = loose.add(all.get(i).weightKg());
                }
            }
        }
        if (pallets.isEmpty() || loose.signum() == 0) {
            return all;
        }
        FedExClient.Parcel first = pallets.get(0);
        pallets.set(0, new FedExClient.Parcel(first.weightKg().add(loose), first.lengthCm(), first.widthCm(), first.heightCm()));
        return pallets;
    }

    /** FedEx's services for the basket, cheapest first; empty when FedEx can't price it. heavy: engines or gearboxes. */
    private List<Option> fedexOptions(BasketView basket, FedExClient.Destination to, boolean heavy, boolean export) {
        List<FedExClient.Parcel> parcels = new ArrayList<>();
        List<FedExClient.Commodity> goods = new ArrayList<>();
        for (BasketView.Line line : basket.lines()) {
            Product p = catalog.findAny(line.productId());
            FedExClient.Parcel box = sizes.parcel(p);
            for (int i = 0; i < line.quantity(); i++) {
                parcels.add(box);
            }
            // Customs value: what the buyer pays for the part, before VAT.
            goods.add(new FedExClient.Commodity("Used car part: " + line.name(), line.quantity(),
                    box.weightKg().multiply(BigDecimal.valueOf(line.quantity())), BasketService.exVat(line.lineTotal())));
        }
        // Free in the UK when every part is light, however much they weigh together.
        boolean allLight = parcels.stream().allMatch(p -> p.weightKg().compareTo(freeUpToKg) <= 0);
        if (heavy) {
            parcels = onThePallet(basket, parcels);
        }
        if (parcels.size() > MAX_PARCELS) {
            return List.of();
        }
        FedExClient.Destination dest = new FedExClient.Destination(
                Countries.UK.equals(Countries.normalise(to.country())) ? normalise(to.postcode()) : tidy(to.postcode()),
                tidy(to.city()), Countries.normalise(to.country()));
        List<FedExClient.Rate> rates;
        try {
            rates = rates(dest, parcels, goods);
        } catch (IOException | RuntimeException e) {
            log.warn("FedEx didn't quote delivery to {} {}: {}", dest.country(), outward(dest.postcode()), e.getMessage());
            return List.of();
        }
        // In the UK: the cheapest service free for light parts, and a ceiling on the rest (the shop pays the difference).
        boolean free = !export && allLight;
        List<Option> options = new ArrayList<>();
        for (FedExClient.Rate r : rates) {
            BigDecimal price = free && options.isEmpty() ? BigDecimal.ZERO.setScale(2) : price(r.chargeExVat(), !export);
            if (!export && price.compareTo(ukMaxPrice) >= 0) {
                // At the ceiling every faster service would cost the shopper the same: only the cheapest is offered.
                if (options.stream().anyMatch(o -> o.price().compareTo(ukMaxPrice) == 0)) {
                    continue;
                }
                price = ukMaxPrice;
            }
            options.add(new Option(r.serviceType(), FEDEX, label(r.serviceName()), price,
                    r.arrives() == null || r.arrives().length() < 10 ? null : r.arrives().substring(0, 10)));
        }
        return List.copyOf(options);
    }

    /** The delivery the order is charged to a UK postcode. */
    public BasketView.Delivery choose(BasketView basket, String postcode, String optionId) {
        return choose(basket, FedExClient.Destination.uk(postcode), optionId);
    }

    /** The delivery the order is charged: the chosen option, priced again here so the browser can't set it. */
    public BasketView.Delivery choose(BasketView basket, FedExClient.Destination to, String optionId) {
        List<Option> options = options(basket, to);
        if (options.isEmpty()) {
            return basket.delivery();
        }
        // The shopper chose a FedEx service, but FedEx can't price the order now: don't switch them to manual delivery.
        boolean fedexChosen = optionId != null && !optionId.equals(MANUAL) && !optionId.equals(COLLECT) && live();
        if (fedexChosen && options.stream().noneMatch(o -> FEDEX.equals(o.method()))) {
            throw new ConflictException("We couldn’t confirm the FedEx delivery price just now. Try again in a moment, or choose manual delivery or collection.");
        }
        return options.stream().filter(o -> o.id().equals(optionId)).findFirst().orElse(options.get(0)).delivery();
    }

    /** What a UK shopper pays: FedEx's charge, the markup and handling fee, and VAT. */
    BigDecimal price(BigDecimal chargeExVat) {
        return price(chargeExVat, true);
    }

    /** withVat is false for exports, which have no UK VAT. */
    BigDecimal price(BigDecimal chargeExVat, boolean withVat) {
        BigDecimal p = chargeExVat.multiply(BigDecimal.ONE.add(markupPercent.movePointLeft(2))).add(handlingFee);
        if (addVat && withVat) {
            p = p.multiply(new BigDecimal("1.20"));
        }
        return p.setScale(2, RoundingMode.HALF_UP);
    }

    private List<FedExClient.Rate> rates(FedExClient.Destination to, List<FedExClient.Parcel> parcels,
                                         List<FedExClient.Commodity> goods) throws IOException {
        // The ship date is in the key: the dates FedEx gives move on with it.
        String key = to + "|" + parcels + "|" + goods + "|" + fedex.shipDate(java.time.ZonedDateTime.now());
        Cached hit = cache.get(key);
        if (hit != null && hit.at().plus(CACHE_FOR).isAfter(Instant.now())) {
            return hit.rates();
        }
        List<FedExClient.Rate> rates = fedex.rates(to, parcels, goods);
        if (cache.size() > MAX_CACHED) {
            cache.clear();
        }
        cache.put(key, new Cached(rates, Instant.now()));
        return rates;
    }

    private static String tidy(String s) {
        return s == null ? "" : s.trim().replaceAll(" {2,}", " ");
    }

    static String label(String serviceName) {
        String name = serviceName.replace("®", "").trim();
        return name.toLowerCase(Locale.UK).startsWith("fedex") ? name : "FedEx " + name;
    }

    /** "sw1a1aa" becomes "SW1A 1AA", as FedEx expects. */
    static String normalise(String postcode) {
        String p = postcode == null ? "" : postcode.replaceAll("\\s", "").toUpperCase(Locale.UK);
        return p.length() > 3 ? p.substring(0, p.length() - 3) + " " + p.substring(p.length() - 3) : p;
    }

    /** Only the first half of a postcode goes in the log. */
    private static String outward(String postcode) {
        String p = normalise(postcode);
        int space = p.indexOf(' ');
        return space > 0 ? p.substring(0, space) : "?";
    }
}
