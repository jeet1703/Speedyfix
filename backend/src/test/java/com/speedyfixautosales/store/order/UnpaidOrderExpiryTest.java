package com.speedyfixautosales.store.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.catalog.CatalogRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

/** What happens to orders left unpaid past the hold, depending on what PayPal says about their payment. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnpaidOrderExpiryTest {

    /** PayPal order id to what the fake PayPal answers on capture: a capture, or an error. */
    static final Map<String, Object> PAYPAL = new java.util.concurrent.ConcurrentHashMap<>();

    @TestConfiguration
    static class FakePayPal {
        @Bean
        @Primary
        PayPalClient fakePayPal() {
            return new PayPalClient("test-client", "test-secret", "sandbox") {
                @Override
                public Capture capture(String paypalOrderId) throws IOException {
                    Object answer = PAYPAL.get(paypalOrderId);
                    if (answer instanceof Capture c) {
                        return c;
                    }
                    throw new PayPalException("ORDER_NOT_APPROVED: the buyer hasn't approved the payment");
                }
            };
        }
    }

    @Autowired
    private OrderPaymentService payments;

    @Autowired
    private OrderStore store;

    @Autowired
    private CatalogRepository catalog;

    /** An order placed three hours ago (past the two-hour hold) for one gm-sensor, with a PayPal payment started. */
    private Order oldOrder(String reference, String paypalOrderId) {
        assertThat(catalog.reduceStock("gm-sensor", 1)).isTrue();
        BasketView.Line line = new BasketView.Line("gm-sensor", "GM coolant temperature sensor", null, null,
                new BigDecimal("18.50"), 1, new BigDecimal("18.50"), 0);
        Order o = new Order(reference, Instant.now().minus(Duration.ofHours(3)), Order.Status.AWAITING_PAYMENT,
                new Order.Customer("Sam", "sam@example.com", "07700 900123"), new Order.Address("1 High St", null, "Leeds", "LS1 1AA"),
                null, List.of(line), new BasketView.Delivery("STANDARD", "Standard tracked delivery", new BigDecimal("9.95")),
                new BigDecimal("18.50"), new BigDecimal("28.45"), new BigDecimal("4.74"), null,
                paypalOrderId == null ? null : new Order.Payment(paypalOrderId, null, null), null);
        store.save(o);
        return o;
    }

    private int stock() {
        return catalog.product("gm-sensor").orElseThrow().stock();
    }

    @Test
    void expiredOrdersArePaidCancelledOrLeftForStaffAccordingToPayPal() {
        int before = stock();
        oldOrder("SF-PAIDLATE", "PP-PAID");
        PAYPAL.put("PP-PAID", new PayPalClient.Capture("COMPLETED", "CAP-1", new BigDecimal("28.45"), "GBP", "SF-PAIDLATE"));
        oldOrder("SF-ABANDON", "PP-NEVER");
        oldOrder("SF-NOPAY", null);
        oldOrder("SF-ODD", "PP-ODD");
        PAYPAL.put("PP-ODD", new PayPalClient.Capture("COMPLETED", "CAP-2", new BigDecimal("1.00"), "GBP", "SF-ODD"));

        payments.cancelExpired();

        // PayPal took the money while we missed it: the order is kept and marked paid.
        Order paid = store.find("SF-PAIDLATE").orElseThrow();
        assertThat(paid.status()).isEqualTo(Order.Status.AWAITING_DISPATCH);
        assertThat(paid.payment().captureId()).isEqualTo("CAP-1");
        // Never paid: cancelled, and its parts are back on sale.
        assertThat(store.find("SF-ABANDON").orElseThrow().status()).isEqualTo(Order.Status.CANCELLED);
        assertThat(store.find("SF-NOPAY").orElseThrow().status()).isEqualTo(Order.Status.CANCELLED);
        // Money moved but doesn't match: left for staff, not cancelled.
        assertThat(store.find("SF-ODD").orElseThrow().status()).isEqualTo(Order.Status.AWAITING_PAYMENT);
        // Two cancelled orders put their parts back; the paid one and the one for staff keep theirs.
        assertThat(stock()).isEqualTo(before - 2);
    }
}
