package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Orders for staff to take payment for, dispatch or cancel. Every route needs a staff session (AdminWebConfig). */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orders;

    public AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    public record StatusChange(@NotNull(message = "Choose a status.") Order.Status status,
                               @Size(max = 60, message = "Keep the courier under 60 characters.") String courier,
                               @Size(max = 60, message = "Keep the tracking number under 60 characters.") String trackingNumber) {
    }

    /** Newest first. */
    @GetMapping
    public List<Order> all() {
        return orders.all();
    }

    @GetMapping("/{reference}")
    public Order get(@PathVariable String reference) {
        return orders.get(reference);
    }

    @PatchMapping("/{reference}")
    public Order update(@PathVariable String reference, @Valid @RequestBody StatusChange change) {
        return orders.setStatus(reference, change.status(), change.courier(), change.trackingNumber());
    }
}
