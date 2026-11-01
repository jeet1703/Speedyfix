package com.speedyfixautosales.store.order;

import java.util.List;
import java.util.Optional;

/** Where orders are kept: PostgreSQL with the "postgres" profile (JdbcOrderStore), otherwise memory. */
public interface OrderStore {

    /** Adds the order, or replaces the one with the same reference. */
    void save(Order order);

    Optional<Order> find(String reference);

    boolean exists(String reference);

    /** Newest first. */
    List<Order> all();

    /** Newest first: orders placed while signed in to this account, plus guest orders with the same email address. */
    List<Order> forCustomer(String customerId, String email);
}
