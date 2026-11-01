package com.speedyfixautosales.store.order;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Orders in memory, for running without a database (a restart clears them). */
@Component
@Profile("!postgres")
public class InMemoryOrderStore implements OrderStore {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        orders.put(order.reference(), order);
    }

    @Override
    public Optional<Order> find(String reference) {
        return Optional.ofNullable(orders.get(reference));
    }

    @Override
    public boolean exists(String reference) {
        return orders.containsKey(reference);
    }

    @Override
    public List<Order> all() {
        return orders.values().stream().sorted(Comparator.comparing(Order::placedAt).reversed()).toList();
    }

    @Override
    public List<Order> forCustomer(String customerId, String email) {
        return all().stream()
                .filter(o -> customerId.equals(o.customerId()) || (email != null && email.equalsIgnoreCase(o.customer().email())))
                .toList();
    }
}
