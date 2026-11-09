package com.speedyfixautosales.store.order;

import com.speedyfixautosales.store.basket.BasketService;
import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.Product;
import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.shipping.DeliveryQuotes;
import com.speedyfixautosales.store.shipping.FedExClient;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final DeliveryQuotes quotes;
    private final ApplicationEventPublisher events;
    private final int maxHeldParts;

    public OrderService(OrderStore orders, BasketService baskets, CatalogRepository catalog, DeliveryQuotes quotes,
                        ApplicationEventPublisher events, @Value("${orders.max-held-parts:10}") int maxHeldParts) {
        this.orders = orders;
        this.baskets = baskets;
        this.catalog = catalog;
        this.quotes = quotes;
        this.events = events;
        this.maxHeldParts = maxHeldParts;
    }

    /** With the cheapest delivery. */
    public Order place(String basketId, Order.Customer customer, Order.Address address, String notes, String customerId) {
        return place(basketId, customer, address, notes, customerId, null);
    }

    /**
     * @param customerId     the signed-in shopper's account, or null for a guest checkout
     * @param deliveryOption the delivery chosen at checkout (DeliveryQuotes), priced again here; null for the cheapest
     */
    public Order place(String basketId, Order.Customer customer, Order.Address address, String notes, String customerId,
                       String deliveryOption) {
        BasketView raw = baskets.get(basketId);
        BasketView.Delivery delivery = quotes.choose(raw, destination(address), deliveryOption);
        // Abroad, a zero-rated export: the parts without UK VAT (the delivery price is already without it).
        BasketView basket = address.export() ? BasketService.forExport(raw, delivery) : BasketService.withDelivery(raw, delivery);
        if (basket.lines().isEmpty()) {
            throw new BadRequestException("Your basket is empty.");
        }
        for (BasketView.Line line : basket.lines()) {
            if (catalog.product(line.productId()).filter(Product::live).isEmpty()) {
                throw new ConflictException("Sorry, " + line.name() + " is no longer for sale. Remove it from your basket and try again.");
            }
        }
        checkHoldLimit(basket.lines(), customer.email(), customerId);
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

    /** Where FedEx delivers the order: postcode, town and country. */
    public static FedExClient.Destination destination(Order.Address address) {
        return new FedExClient.Destination(address.postcode(), address.town(), address.country());
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
    public Order setStatus(String reference, Order.Status status, String courier, String trackingNumber) {
        return setStatus(reference, status, courier, trackingNumber, false);
    }

    /**
     * As above, and a dispatched order can be cancelled too (sent back, refused, lost in the post). Its parts have left
     * the shop, so they only go back in stock with restock, once they're back; the dispatch details are kept.
     */
    public synchronized Order setStatus(String reference, Order.Status status, String courier, String trackingNumber,
                                        boolean restock) {
        Order order = get(reference);
        if (order.status() == status) {
            return order;
        }
        if (order.status() == Order.Status.CANCELLED) {
            throw new ConflictException("This order is already cancelled.");
        }
        boolean dispatched = order.status() == Order.Status.DISPATCHED;
        if (dispatched && status != Order.Status.CANCELLED) {
            throw new ConflictException("This order is already dispatched.");
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
            case CANCELLED -> {
                if (!dispatched || restock) {
                    order.lines().forEach(l -> catalog.reduceStock(l.productId(), -l.quantity()));
                }
                dispatch = order.dispatch();
            }
        }
        Order updated = order.withStatus(status, dispatch);
        orders.save(updated);
        // The invoice and emails (OrderEmails); they never stop the order changing.
        if (status == Order.Status.AWAITING_DISPATCH) {
            events.publishEvent(new OrderPaid(updated));
        } else if (status == Order.Status.CANCELLED) {
            events.publishEvent(new OrderCancelled(updated));
        }
        return updated;
    }

    /**
     * An unpaid order holds its parts off sale, so one shopper (by account or email) can only hold so many at once:
     * enough for any real order, not enough to empty the shop.
     */
    private void checkHoldLimit(List<BasketView.Line> lines, String email, String customerId) {
        if (maxHeldParts <= 0) {
            return;
        }
        int held = orders.forCustomer(customerId, email).stream()
                .filter(o -> o.status() == Order.Status.AWAITING_PAYMENT)
                .mapToInt(o -> o.lines().stream().mapToInt(BasketView.Line::quantity).sum()).sum();
        int wanted = lines.stream().mapToInt(BasketView.Line::quantity).sum();
        if (held + wanted > maxHeldParts) {
            throw new ConflictException(held == 0
                    ? "You can order up to " + maxHeldParts + " parts at a time. Remove some from your basket, or call us for a bigger order."
                    : "You already have " + held + (held == 1 ? " part" : " parts") + " reserved in unpaid orders, and you can hold up to "
                    + maxHeldParts + " at a time. Pay for those orders first (the link is in your account), or call us.");
        }
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
