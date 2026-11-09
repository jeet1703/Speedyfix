package com.speedyfixautosales.store.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.speedyfixautosales.store.basket.BasketView;
import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.common.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** Staff cancelling an order that has already been sent: it came back, was refused or was lost. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CancelDispatchedOrderTest {

    @Autowired
    private OrderService orders;

    @Autowired
    private OrderStore store;

    @Autowired
    private CatalogRepository catalog;

    /** A paid order for one gm-sensor, sent with Royal Mail. */
    private void dispatchedOrder(String reference) {
        assertThat(catalog.reduceStock("gm-sensor", 1)).isTrue();
        BasketView.Line line = new BasketView.Line("gm-sensor", "GM coolant temperature sensor", null, null,
                new BigDecimal("18.50"), 1, new BigDecimal("18.50"), 0);
        store.save(new Order(reference, Instant.now(), Order.Status.DISPATCHED,
                new Order.Customer("Sam", "sam@example.com", "07700 900123"), new Order.Address("1 High St", null, "Leeds", "LS1 1AA"),
                null, List.of(line), new BasketView.Delivery("STANDARD", "Standard tracked delivery", new BigDecimal("9.95")),
                new BigDecimal("18.50"), new BigDecimal("28.45"), new BigDecimal("4.74"),
                new Order.Dispatch("Royal Mail", "RM123", Instant.now()), null, null));
    }

    private int stock() {
        return catalog.product("gm-sensor").orElseThrow().stock();
    }

    @Test
    void aDispatchedOrderCanBeCancelledAndOnlyRestockedWhenThePartsAreBack() {
        int before = stock();
        dispatchedOrder("SF-LOSTPOST");
        Order lost = orders.setStatus("SF-LOSTPOST", Order.Status.CANCELLED, null, null, false);
        assertThat(lost.status()).isEqualTo(Order.Status.CANCELLED);
        assertThat(lost.dispatch().trackingNumber()).isEqualTo("RM123");
        assertThat(stock()).isEqualTo(before - 1);

        dispatchedOrder("SF-RETURNED");
        orders.setStatus("SF-RETURNED", Order.Status.CANCELLED, null, null, true);
        assertThat(stock()).isEqualTo(before - 1);
    }

    @Test
    void aDispatchedOrderCantGoBackAndACancelledOneStaysCancelled() {
        dispatchedOrder("SF-SENTONCE");
        assertThatThrownBy(() -> orders.setStatus("SF-SENTONCE", Order.Status.AWAITING_DISPATCH, null, null))
                .isInstanceOf(ConflictException.class);
        orders.setStatus("SF-SENTONCE", Order.Status.CANCELLED, null, null);
        assertThatThrownBy(() -> orders.setStatus("SF-SENTONCE", Order.Status.DISPATCHED, "DPD", null))
                .isInstanceOf(ConflictException.class);
    }
}
