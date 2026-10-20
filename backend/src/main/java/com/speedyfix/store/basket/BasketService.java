package com.speedyfix.store.basket;

import com.speedyfix.store.catalog.CatalogService;
import com.speedyfix.store.catalog.Product;
import com.speedyfix.store.catalog.Shipping;
import com.speedyfix.store.common.ConflictException;
import com.speedyfix.store.common.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory baskets for the prototype. Delivery rules match the design:
 * pallet items cost £79; otherwise tracked parcel delivery is £9.95, free over £100.
 */
@Service
public class BasketService {

    static final BigDecimal PALLET_PRICE = new BigDecimal("79.00");
    static final BigDecimal STANDARD_PRICE = new BigDecimal("9.95");
    static final BigDecimal FREE_DELIVERY_FROM = new BigDecimal("100.00");

    private final Map<String, Basket> baskets = new ConcurrentHashMap<>();
    private final CatalogService catalog;

    public BasketService(CatalogService catalog) {
        this.catalog = catalog;
    }

    public BasketView create() {
        Basket basket = new Basket();
        baskets.put(basket.id(), basket);
        return view(basket);
    }

    public BasketView get(String basketId) {
        return view(basket(basketId));
    }

    public BasketView addItem(String basketId, String productId, int quantity) {
        Basket basket = basket(basketId);
        Product product = catalog.getProduct(productId);
        synchronized (basket) {
            int next = basket.quantityOf(productId) + quantity;
            if (next > product.stock()) {
                throw new ConflictException(stockMessage(product));
            }
            basket.set(productId, next);
        }
        return view(basket);
    }

    public BasketView setQuantity(String basketId, String productId, int quantity) {
        Basket basket = basket(basketId);
        Product product = catalog.getProduct(productId);
        if (quantity > product.stock()) {
            throw new ConflictException(stockMessage(product));
        }
        basket.set(productId, quantity);
        return view(basket);
    }

    public BasketView remove(String basketId, String productId) {
        Basket basket = basket(basketId);
        basket.set(productId, 0);
        return view(basket);
    }

    /** Empties the basket once its contents have become an order. */
    public void clear(String basketId) {
        Basket basket = basket(basketId);
        basket.snapshot().keySet().forEach(productId -> basket.set(productId, 0));
    }

    private Basket basket(String id) {
        Basket basket = id == null ? null : baskets.get(id);
        if (basket == null) {
            throw new NotFoundException("This basket has expired. Start a new one by adding a part.");
        }
        return basket;
    }

    BasketView view(Basket basket) {
        List<BasketView.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        boolean pallet = false;
        for (Map.Entry<String, Integer> e : basket.snapshot().entrySet()) {
            Product p = catalog.getProduct(e.getKey());
            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(e.getValue()));
            lines.add(new BasketView.Line(p.id(), p.name(), p.reference(), p.imageUrl(), money(p.price()), e.getValue(),
                    money(lineTotal), p.stock()));
            subtotal = subtotal.add(lineTotal);
            count += e.getValue();
            pallet |= p.shipping() == Shipping.PALLET;
        }
        BasketView.Delivery delivery = delivery(lines.isEmpty(), pallet, subtotal);
        BigDecimal total = subtotal.add(delivery.price());
        BigDecimal vat = total.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        return new BasketView(basket.id(), List.copyOf(lines), count, money(subtotal), delivery, money(total), vat);
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
