package com.speedyfix.store.order;

import com.speedyfix.store.basket.BasketService;
import com.speedyfix.store.basket.BasketView;
import com.speedyfix.store.catalog.CatalogRepository;
import com.speedyfix.store.common.BadRequestException;
import com.speedyfix.store.common.ConflictException;
import com.speedyfix.store.common.NotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Turns a basket into an order and takes the parts out of stock. In memory for the prototype;
 * orders hold personal data, so the production store needs access control and a retention policy.
 */
@Service
public class OrderService {

    // No 0/O or 1/I, so references are easy to read out over the phone.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final BasketService baskets;
    private final CatalogRepository catalog;

    public OrderService(BasketService baskets, CatalogRepository catalog) {
        this.baskets = baskets;
        this.catalog = catalog;
    }

    public Order place(String basketId, Order.Customer customer, Order.Address address, String notes) {
        BasketView basket = baskets.get(basketId);
        if (basket.lines().isEmpty()) {
            throw new BadRequestException("Your basket is empty.");
        }
        reserveStock(basket.lines());
        Order order = new Order(newReference(), Instant.now(), Order.Status.AWAITING_PAYMENT, customer, address,
                notes == null || notes.isBlank() ? null : notes.trim(), basket.lines(), basket.delivery(),
                basket.subtotal(), basket.total(), basket.vatIncluded());
        orders.put(order.reference(), order);
        baskets.clear(basketId);
        return order;
    }

    public Order get(String reference) {
        Order order = reference == null ? null : orders.get(reference.toUpperCase());
        if (order == null) {
            throw new NotFoundException("We couldn’t find that order.");
        }
        return order;
    }

    /** All or nothing: if any line is short, puts back what was already taken. */
    private void reserveStock(List<BasketView.Line> lines) {
        List<BasketView.Line> taken = new ArrayList<>();
        for (BasketView.Line line : lines) {
            if (!catalog.reduceStock(line.productId(), line.quantity())) {
                taken.forEach(t -> catalog.reduceStock(t.productId(), -t.quantity()));
                throw new ConflictException("Sorry, " + line.name() + " has just sold out. Update your basket and try again.");
            }
            taken.add(line);
        }
    }

    private String newReference() {
        String ref;
        do {
            StringBuilder sb = new StringBuilder("SF-");
            for (int i = 0; i < 8; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            ref = sb.toString();
        } while (orders.containsKey(ref));
        return ref;
    }
}
