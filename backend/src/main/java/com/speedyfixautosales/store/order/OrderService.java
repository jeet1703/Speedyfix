package com.speedyfixautosales.store.order;

import com.speedyfixautosales.store.basket.BasketService;
import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.Product;
import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.NotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Turns a basket into an order and takes the parts out of stock. Orders are kept by an OrderStore (the database
 * with the "postgres" profile); they hold personal data, so only staff and the shopper's own account see them listed.
 */
@Service
public class OrderService {

    // No 0/O or 1/I, so references are easy to read out over the phone.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderStore orders;
    private final BasketService baskets;
    private final CatalogRepository catalog;

    public OrderService(OrderStore orders, BasketService baskets, CatalogRepository catalog) {
        this.orders = orders;
        this.baskets = baskets;
        this.catalog = catalog;
    }

    /** @param customerId the signed-in shopper's account, or null for a guest checkout */
    public Order place(String basketId, Order.Customer customer, Order.Address address, String notes, String customerId) {
        BasketView basket = baskets.get(basketId);
        if (basket.lines().isEmpty()) {
            throw new BadRequestException("Your basket is empty.");
        }
        for (BasketView.Line line : basket.lines()) {
            if (catalog.product(line.productId()).filter(Product::live).isEmpty()) {
                throw new ConflictException("Sorry, " + line.name() + " is no longer for sale. Remove it from your basket and try again.");
            }
        }
        reserveStock(basket.lines());
        Order order = new Order(newReference(), Instant.now(), Order.Status.AWAITING_PAYMENT, customer, address,
                notes == null || notes.isBlank() ? null : notes.trim(), basket.lines(), basket.delivery(),
                basket.subtotal(), basket.total(), basket.vatIncluded(), null, null, customerId);
        try {
            orders.save(order);
        } catch (RuntimeException e) {
            basket.lines().forEach(l -> catalog.reduceStock(l.productId(), -l.quantity()));
            throw e;
        }
        baskets.clear(basketId);
        return order;
    }

    /** Newest first, for the admin. */
    public List<Order> all() {
        return orders.all();
    }

    /** A shopper's own orders, newest first, including guest orders placed with their account's email. */
    public List<Order> forCustomer(String customerId, String email) {
        return orders.forCustomer(customerId, email);
    }

    public Order get(String reference) {
        return (reference == null ? Optional.<Order>empty() : orders.find(reference.trim().toUpperCase()))
                .orElseThrow(() -> new NotFoundException("We couldn’t find that order."));
    }

    /**
     * Staff move an order on: AWAITING_PAYMENT to AWAITING_DISPATCH once paid, then DISPATCHED with the courier and
     * tracking number. Any order not yet dispatched can be cancelled, which puts its parts back in stock.
     */
    public synchronized Order setStatus(String reference, Order.Status status, String courier, String trackingNumber) {
        Order order = get(reference);
        if (order.status() == status) {
            return order;
        }
        if (order.status() == Order.Status.DISPATCHED || order.status() == Order.Status.CANCELLED) {
            throw new ConflictException("This order is already " + (order.status() == Order.Status.DISPATCHED ? "dispatched" : "cancelled") + ".");
        }
        Order.Dispatch dispatch = null;
        switch (status) {
            case AWAITING_PAYMENT -> throw new BadRequestException("An order can’t go back to awaiting payment.");
            case AWAITING_DISPATCH -> {
                if (order.status() != Order.Status.AWAITING_PAYMENT) {
                    throw new ConflictException("This order is already marked as paid.");
                }
            }
            case DISPATCHED -> {
                if (order.status() != Order.Status.AWAITING_DISPATCH) {
                    throw new ConflictException("Mark the order as paid before dispatching it.");
                }
                if (courier == null || courier.isBlank()) {
                    throw new BadRequestException("Enter the courier.");
                }
                dispatch = new Order.Dispatch(courier.trim(), trackingNumber == null || trackingNumber.isBlank() ? null : trackingNumber.trim(), Instant.now());
            }
            case CANCELLED -> order.lines().forEach(l -> catalog.reduceStock(l.productId(), -l.quantity()));
        }
        Order updated = order.withStatus(status, dispatch);
        orders.save(updated);
        return updated;
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
        } while (orders.exists(ref));
        return ref;
    }
}
