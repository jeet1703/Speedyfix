package com.speedyfixautosales.store.order;

import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.PaymentFailedException;
import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.config.SiteProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Online payment for an order through PayPal Checkout: the shopper's browser shows PayPal's buttons (PayPal, or a
 * debit or credit card), this server creates the PayPal order and, once the shopper approves, captures it. Only a
 * capture PayPal confirms as COMPLETED, in GBP, for exactly the order's total and with the order's reference, marks
 * the order paid (AWAITING_DISPATCH); nothing the browser says counts as payment.
 *
 * With PayPal set up, orders left unpaid for ORDERS_UNPAID_EXPIRY (2 hours by default) are cancelled, which puts their
 * parts back on sale. Without PayPal, staff mark orders paid by hand and nothing expires.
 */
@Service
public class OrderPaymentService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentService.class);

    private final OrderStore orders;
    private final OrderService orderService;
    private final PayPalClient paypal;
    private final String brandName;
    private final Duration unpaidExpiry;
    private final ApplicationEventPublisher events;

    public OrderPaymentService(OrderStore orders, OrderService orderService, PayPalClient paypal, SiteProperties site,
                               @Value("${orders.unpaid-expiry:PT2H}") Duration unpaidExpiry, ApplicationEventPublisher events) {
        this.events = events;
        this.orders = orders;
        this.orderService = orderService;
        this.paypal = paypal;
        this.brandName = site.name();
        this.unpaidExpiry = unpaidExpiry;
    }

    /** How long an unpaid order holds its parts, or null when unpaid orders don't expire. */
    public Duration unpaidExpiry() {
        return paypal.enabled() && !unpaidExpiry.isZero() ? unpaidExpiry : null;
    }

    /** Creates the PayPal payment for an unpaid order and returns PayPal's order id for the buttons. */
    public synchronized String startPayPal(String reference) {
        if (!paypal.enabled()) {
            throw new UnavailableException("Online payment isn’t set up yet. Call us to pay by card over the phone.");
        }
        Order order = orderService.get(reference);
        requireUnpaid(order);
        try {
            String paypalOrderId = paypal.createOrder(order, brandName);
            orders.save(order.withPayment(new Order.Payment(paypalOrderId, null, null)));
            return paypalOrderId;
        } catch (IOException e) {
            log.warn("PayPal order for {} not created: {}", order.reference(), e.getMessage());
            throw new PaymentFailedException("We couldn’t start the PayPal payment. Try again in a moment.");
        }
    }

    /** Takes the payment the shopper approved in PayPal and marks the order paid. Safe to call twice. */
    public synchronized Order capturePayPal(String reference, String paypalOrderId) {
        Order order = orderService.get(reference);
        if (order.payment() == null || !order.payment().paypalOrderId().equals(paypalOrderId)) {
            throw new ConflictException("That payment doesn’t belong to this order. Refresh the page and try again.");
        }
        if (order.payment().captureId() != null) {
            return order;
        }
        requireUnpaid(order);
        PayPalClient.Capture c;
        try {
            c = paypal.capture(paypalOrderId);
        } catch (IOException e) {
            log.warn("PayPal capture for {} failed: {}", order.reference(), e.getMessage());
            throw new PaymentFailedException(e.getMessage().startsWith("INSTRUMENT_DECLINED")
                    ? "Your payment was declined. Try another card or payment method."
                    : "The payment didn’t go through, and you haven’t been charged. Try again.");
        }
        boolean matches = "COMPLETED".equals(c.status()) && "GBP".equals(c.currency()) && c.amount() != null
                && c.amount().compareTo(order.total()) == 0 && order.reference().equals(c.reference());
        if (!matches) {
            // Money may have moved without matching this order: staff check it in PayPal rather than us guessing.
            log.error("PayPal capture for {} doesn't match the order: {}", order.reference(), c);
            // Staff are emailed (once per order, however often this repeats).
            events.publishEvent(new PaymentNeedsChecking(order, c.status(), c.currency(), c.amount()));
            throw new PaymentMismatchException("We couldn’t confirm your payment. Please call us with your order number before trying again.");
        }
        orders.save(order.withPayment(new Order.Payment(paypalOrderId, c.captureId(), Instant.now())));
        return orderService.setStatus(order.reference(), Order.Status.AWAITING_DISPATCH, null, null);
    }

    /**
     * Cancels orders left unpaid too long, so one-off parts don't stay reserved by abandoned checkouts. Before
     * cancelling an order whose PayPal payment was started, PayPal is asked: a payment the shopper approved, or one
     * PayPal took while this server missed the answer (a restart, a lost connection), is captured or recorded and the
     * order kept. Synchronized with capturePayPal, so an order can't be cancelled while its payment is being taken.
     */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT3M")
    synchronized void cancelExpired() {
        Duration expiry = unpaidExpiry();
        if (expiry == null) {
            return;
        }
        Instant cutoff = Instant.now().minus(expiry);
        for (Order o : orderService.all()) {
            if (o.status() != Order.Status.AWAITING_PAYMENT || !o.placedAt().isBefore(cutoff)) {
                continue;
            }
            if (o.payment() != null && o.payment().captureId() == null) {
                try {
                    capturePayPal(o.reference(), o.payment().paypalOrderId());
                    log.warn("Order {} was paid in PayPal but not recorded; now marked paid", o.reference());
                    continue;
                } catch (PaymentMismatchException mismatch) {
                    // Money may have moved: leave the order for staff to check in PayPal (logged as an error).
                    continue;
                } catch (RuntimeException notPaid) {
                    // Never approved, or PayPal refused it: nothing was taken, so cancel it below.
                }
            }
            try {
                orderService.setStatus(o.reference(), Order.Status.CANCELLED, null, null);
                log.info("Order {} cancelled: unpaid after {}", o.reference(), expiry);
            } catch (RuntimeException e) {
                log.warn("Couldn’t cancel unpaid order {}: {}", o.reference(), e.getMessage());
            }
        }
    }

    /** PayPal took a payment that doesn't match the order (amount, currency or reference): staff must look. */
    static class PaymentMismatchException extends PaymentFailedException {
        private static final long serialVersionUID = 1L;

        PaymentMismatchException(String message) {
            super(message);
        }
    }

    private static void requireUnpaid(Order order) {
        if (order.status() == Order.Status.CANCELLED) {
            throw new ConflictException("This order was cancelled because it wasn’t paid in time. Add the parts to your basket again.");
        }
        if (order.status() != Order.Status.AWAITING_PAYMENT) {
            throw new ConflictException("This order is already paid.");
        }
    }
}
