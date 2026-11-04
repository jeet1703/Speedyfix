package com.speedyfixautosales.store.basket;

import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.catalog.Product;
import com.speedyfixautosales.store.catalog.Shipping;
import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Baskets, kept by a BasketStore (the database with the "postgres" profile). Delivery rules match the design:
 * pallet items cost £79; otherwise tracked parcel delivery is £9.95, free over £100.
 */
@Service
public class BasketService {

    static final BigDecimal PALLET_PRICE = new BigDecimal("79.00");
    static final BigDecimal STANDARD_PRICE = new BigDecimal("9.95");
    public static final BigDecimal FREE_DELIVERY_FROM = new BigDecimal("100.00");
    static final Duration IDLE_EXPIRY = Duration.ofDays(7);

    private final BasketStore baskets;
    private final CatalogService catalog;

    public BasketService(BasketStore baskets, CatalogService catalog) {
        this.baskets = baskets;
        this.catalog = catalog;
    }

    /** Drops baskets nobody has changed for a week, so storage doesn't grow forever. The shopper's next add starts a new one. */
    @Scheduled(fixedDelayString = "PT1H")
    void removeIdleBaskets() {
        baskets.removeIdle(Instant.now().minus(IDLE_EXPIRY));
    }

    public BasketView create() {
        return view(baskets.create(), Map.of());
    }

    public BasketView get(String basketId) {
        return view(basketId, items(basketId));
    }

    /** Synchronized so two quick adds of the same part can't both pass the stock check. */
    public synchronized BasketView addItem(String basketId, String productId, int quantity) {
        Map<String, Integer> items = items(basketId);
        Product product = catalog.getProduct(productId);
        int next = items.getOrDefault(productId, 0) + quantity;
        if (next > product.stock()) {
            throw new ConflictException(stockMessage(product));
        }
        baskets.set(basketId, productId, next);
        return get(basketId);
    }

    public BasketView setQuantity(String basketId, String productId, int quantity) {
        items(basketId);
        Product product = catalog.findAny(productId);
        if (quantity > product.stock()) {
            throw new ConflictException(stockMessage(product));
        }
        baskets.set(basketId, productId, quantity);
        return get(basketId);
    }

    public BasketView remove(String basketId, String productId) {
        items(basketId);
        baskets.set(basketId, productId, 0);
        return get(basketId);
    }

    /** Empties the basket once its contents have become an order. */
    public void clear(String basketId) {
        items(basketId);
        baskets.clear(basketId);
    }

    private Map<String, Integer> items(String id) {
        return baskets.items(id)
                .orElseThrow(() -> new NotFoundException("This basket has expired. Start a new one by adding a part."));
    }

    BasketView view(String basketId, Map<String, Integer> items) {
        List<BasketView.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        boolean pallet = false;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Product p = catalog.findAny(e.getKey());
            // The sale price when the part is on sale: what the order (and PayPal) will charge.
            BigDecimal lineTotal = p.sellingPrice().multiply(BigDecimal.valueOf(e.getValue()));
            lines.add(new BasketView.Line(p.id(), p.name(), p.reference(), p.imageUrl(), money(p.sellingPrice()), e.getValue(),
                    money(lineTotal), p.stock()));
            subtotal = subtotal.add(lineTotal);
            count += e.getValue();
            pallet |= p.shipping() == Shipping.PALLET;
        }
        return totals(new BasketView(basketId, List.copyOf(lines), count, money(subtotal), null, null, null),
                delivery(lines.isEmpty(), pallet, subtotal));
    }

    /** The basket with another delivery (a FedEx quote, say), and its total and VAT worked out again. */
    public static BasketView withDelivery(BasketView basket, BasketView.Delivery delivery) {
        return totals(basket, delivery);
    }

    /**
     * The basket for an export (an address outside the UK VAT area): every price without the 20% VAT, the delivery as
     * given (export prices have no VAT), and no VAT in the total.
     */
    public static BasketView forExport(BasketView basket, BasketView.Delivery delivery) {
        List<BasketView.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BasketView.Line l : basket.lines()) {
            BigDecimal unit = exVat(l.unitPrice());
            BigDecimal lineTotal = money(unit.multiply(BigDecimal.valueOf(l.quantity())));
            lines.add(new BasketView.Line(l.productId(), l.name(), l.reference(), l.imageUrl(), unit, l.quantity(), lineTotal, l.stock()));
            subtotal = subtotal.add(lineTotal);
        }
        BigDecimal total = money(subtotal.add(delivery.price()));
        return new BasketView(basket.id(), List.copyOf(lines), basket.itemCount(), money(subtotal), delivery, total, money(BigDecimal.ZERO));
    }

    /** A VAT-inclusive price without its 20% VAT, to the penny. */
    public static BigDecimal exVat(BigDecimal withVat) {
        return withVat.divide(new BigDecimal("1.20"), 2, RoundingMode.HALF_UP);
    }

    private static BasketView totals(BasketView b, BasketView.Delivery delivery) {
        BigDecimal total = b.subtotal().add(delivery.price());
        BigDecimal vat = total.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        return new BasketView(b.id(), b.lines(), b.itemCount(), b.subtotal(), delivery, money(total), vat);
    }

    static BasketView.Delivery delivery(boolean empty, boolean pallet, BigDecimal subtotal) {
        if (empty) {
            return new BasketView.Delivery("NONE", null, money(BigDecimal.ZERO));
        }
        if (pallet) {
            return new BasketView.Delivery("PALLET", "Pallet delivery", PALLET_PRICE);
        }
        BigDecimal price = subtotal.compareTo(FREE_DELIVERY_FROM) >= 0 ? money(BigDecimal.ZERO) : STANDARD_PRICE;
        return new BasketView.Delivery("STANDARD", "Standard tracked delivery", price);
    }

    private static String stockMessage(Product p) {
        return p.stock() == 1 ? "Only 1 of this part is in stock." : "Only " + p.stock() + " of this part are in stock.";
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
