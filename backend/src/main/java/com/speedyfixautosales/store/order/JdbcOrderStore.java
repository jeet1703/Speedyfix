package com.speedyfixautosales.store.order;

import com.speedyfixautosales.store.basket.BasketView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Orders and their lines in PostgreSQL (orders, order_items). Each line keeps the name and price it was sold at. */
@Component
@Profile("postgres")
public class JdbcOrderStore implements OrderStore {

    private final JdbcTemplate jdbc;

    public JdbcOrderStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Order o) {
        Order.Dispatch d = o.dispatch();
        int updated = jdbc.update("""
                UPDATE orders SET status = ?, dispatch_courier = ?, dispatch_tracking = ?, dispatched_at = ?,
                    paid_at = CASE WHEN ? = 'AWAITING_DISPATCH' AND paid_at IS NULL THEN now() ELSE paid_at END,
                    paypal_order_id = ?, payment_capture_id = ?
                WHERE reference = ?""",
                o.status().name(), d == null ? null : d.courier(), d == null ? null : d.trackingNumber(),
                d == null ? null : Timestamp.from(d.dispatchedAt()), o.status().name(),
                o.payment() == null ? null : o.payment().paypalOrderId(), o.payment() == null ? null : o.payment().captureId(),
                o.reference());
        if (updated > 0) {
            return;
        }
        Order.Customer c = o.customer();
        Order.Address a = o.address();
        jdbc.update("""
                INSERT INTO orders (reference, placed_at, status, customer_id, customer_name, customer_email, customer_phone,
                    address_line1, address_line2, address_town, address_postcode, notes, delivery_method, delivery_label,
                    delivery_price, subtotal, total, vat_included, dispatch_courier, dispatch_tracking, dispatched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                o.reference(), Timestamp.from(o.placedAt()), o.status().name(), o.customerId(), c.name(), c.email(), c.phone(),
                a.line1(), a.line2(), a.town(), a.postcode(), o.notes(), o.delivery().method(), o.delivery().label(),
                o.delivery().price(), o.subtotal(), o.total(), o.vatIncluded(), d == null ? null : d.courier(),
                d == null ? null : d.trackingNumber(), d == null ? null : Timestamp.from(d.dispatchedAt()));
        int position = 0;
        for (BasketView.Line l : o.lines()) {
            jdbc.update("""
                    INSERT INTO order_items (order_reference, position, product_id, name, reference, image_url, unit_price, quantity, line_total)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    o.reference(), position++, l.productId(), l.name(), l.reference(), l.imageUrl(), l.unitPrice(), l.quantity(),
                    l.lineTotal());
        }
    }

    @Override
    public Optional<Order> find(String reference) {
        return load("WHERE o.reference = ?", reference).stream().findFirst();
    }

    @Override
    public boolean exists(String reference) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM orders WHERE reference = ?)", Boolean.class, reference));
    }

    @Override
    public List<Order> all() {
        return load("");
    }

    @Override
    public List<Order> forCustomer(String customerId, String email) {
        return load("WHERE o.customer_id = ? OR lower(o.customer_email) = lower(?)", customerId, email == null ? "" : email);
    }

    /** @param where a condition on the orders table, aliased "o" */
    private List<Order> load(String where, Object... args) {
        Map<String, List<BasketView.Line>> lines = new LinkedHashMap<>();
        jdbc.query("SELECT i.* FROM order_items i JOIN orders o ON o.reference = i.order_reference " + where
                        + " ORDER BY i.order_reference, i.position",
                rs -> {
                    lines.computeIfAbsent(rs.getString("order_reference"), k -> new ArrayList<>()).add(new BasketView.Line(
                            rs.getString("product_id"), rs.getString("name"), rs.getString("reference"), rs.getString("image_url"),
                            rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total"), 0));
                }, args);
        return jdbc.query("SELECT o.* FROM orders o " + where + " ORDER BY o.placed_at DESC",
                (rs, row) -> order(rs, lines.getOrDefault(rs.getString("reference"), List.of())), args);
    }

    private static Order order(ResultSet rs, List<BasketView.Line> lines) throws SQLException {
        Timestamp dispatchedAt = rs.getTimestamp("dispatched_at");
        String courier = rs.getString("dispatch_courier");
        return new Order(rs.getString("reference"), rs.getTimestamp("placed_at").toInstant(),
                Order.Status.valueOf(rs.getString("status")),
                new Order.Customer(rs.getString("customer_name"), rs.getString("customer_email"), rs.getString("customer_phone")),
                new Order.Address(rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("address_town"),
                        rs.getString("address_postcode")),
                rs.getString("notes"), List.copyOf(lines),
                new BasketView.Delivery(rs.getString("delivery_method"), rs.getString("delivery_label"), rs.getBigDecimal("delivery_price")),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("total"), rs.getBigDecimal("vat_included"),
                courier == null || dispatchedAt == null ? null : new Order.Dispatch(courier, rs.getString("dispatch_tracking"), dispatchedAt.toInstant()),
                payment(rs), rs.getString("customer_id"));
    }

    private static Order.Payment payment(ResultSet rs) throws SQLException {
        String paypalOrder = rs.getString("paypal_order_id");
        if (paypalOrder == null) {
            return null;
        }
        Timestamp paid = rs.getTimestamp("paid_at");
        String capture = rs.getString("payment_capture_id");
        return new Order.Payment(paypalOrder, capture, capture == null || paid == null ? null : paid.toInstant());
    }
}
