package com.speedyfixautosales.store.mail;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Order emails waiting to go, sent, or given up on: the email_outbox table with the "postgres" profile, memory
 * without it. Writing the row first and sending later means a mail outage never loses an email or holds up an order,
 * and one row per order and kind means the same email can't be sent twice.
 */
@Component
public class EmailOutbox {

    public enum Kind { CUSTOMER_INVOICE, STAFF_NEW_ORDER, CUSTOMER_CANCELLED, STAFF_PAYMENT_CHECK }

    public enum Status { PENDING, SENT, FAILED }

    /** details: what the email needs that the order doesn't hold (what PayPal reported, say); usually null. */
    public record Entry(long id, String orderReference, Kind kind, String recipient, Status status, int attempts,
                        String lastError, Instant createdAt, Instant nextAttemptAt, Instant sentAt, String details) {
    }

    private static final String COLUMNS = "id, order_reference, kind, recipient, status, attempts, last_error, created_at, next_attempt_at, sent_at, details";

    /** Null without the database. */
    private final JdbcTemplate jdbc;
    private final Map<Long, Entry> memory = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1);

    public EmailOutbox(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc.getIfAvailable();
    }

    /** Queues the email unless this order already has one of this kind. */
    public void queue(String orderReference, Kind kind, String recipient) {
        queue(orderReference, kind, recipient, null);
    }

    public synchronized void queue(String orderReference, Kind kind, String recipient, String details) {
        String d = details == null || details.length() <= 500 ? details : details.substring(0, 500);
        if (jdbc != null) {
            jdbc.update("INSERT INTO email_outbox (order_reference, kind, recipient, status, details) VALUES (?, ?, ?, 'PENDING', ?) "
                    + "ON CONFLICT (order_reference, kind) DO NOTHING", orderReference, kind.name(), recipient, d);
            return;
        }
        if (find(orderReference, kind) == null) {
            long id = ids.getAndIncrement();
            Instant now = Instant.now();
            memory.put(id, new Entry(id, orderReference, kind, recipient, Status.PENDING, 0, null, now, now, null, d));
        }
    }

    /** Queues the email to go (again) now, whether or not it was sent before: for staff's "Email the invoice again". */
    public synchronized void requeue(String orderReference, Kind kind, String recipient) {
        if (jdbc != null) {
            jdbc.update("""
                    INSERT INTO email_outbox (order_reference, kind, recipient, status) VALUES (?, ?, ?, 'PENDING')
                    ON CONFLICT (order_reference, kind) DO UPDATE SET recipient = EXCLUDED.recipient, status = 'PENDING',
                        attempts = 0, last_error = NULL, next_attempt_at = now(), sent_at = NULL""",
                    orderReference, kind.name(), recipient);
            return;
        }
        Entry old = find(orderReference, kind);
        long id = old == null ? ids.getAndIncrement() : old.id();
        Instant now = Instant.now();
        memory.put(id, new Entry(id, orderReference, kind, recipient, Status.PENDING, 0, null,
                old == null ? now : old.createdAt(), now, null, old == null ? null : old.details()));
    }

    /** Pending emails whose time has come, oldest first. */
    public List<Entry> due(Instant now, int limit) {
        if (jdbc != null) {
            return jdbc.query("SELECT " + COLUMNS + " FROM email_outbox WHERE status = 'PENDING' AND next_attempt_at <= ? "
                    + "ORDER BY next_attempt_at LIMIT ?", EmailOutbox::entry, Timestamp.from(now), limit);
        }
        return memory.values().stream()
                .filter(e -> e.status() == Status.PENDING && !e.nextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(Entry::nextAttemptAt)).limit(limit).toList();
    }

    /** Oldest first. */
    public List<Entry> forOrder(String orderReference) {
        if (jdbc != null) {
            return jdbc.query("SELECT " + COLUMNS + " FROM email_outbox WHERE order_reference = ? ORDER BY id",
                    EmailOutbox::entry, orderReference);
        }
        return memory.values().stream().filter(e -> e.orderReference().equals(orderReference))
                .sorted(Comparator.comparingLong(Entry::id)).toList();
    }

    public void sent(long id) {
        Instant now = Instant.now();
        if (jdbc != null) {
            jdbc.update("UPDATE email_outbox SET status = 'SENT', attempts = attempts + 1, last_error = NULL, sent_at = ? WHERE id = ?",
                    Timestamp.from(now), id);
            return;
        }
        memory.computeIfPresent(id, (k, e) -> new Entry(id, e.orderReference(), e.kind(), e.recipient(), Status.SENT,
                e.attempts() + 1, null, e.createdAt(), e.nextAttemptAt(), now, e.details()));
    }

    /** A failed try: tried again at retryAt, or, with null, given up on. */
    public void failed(long id, String error, Instant retryAt) {
        String message = error == null ? "Unknown error" : error.length() > 300 ? error.substring(0, 300) : error;
        Status status = retryAt == null ? Status.FAILED : Status.PENDING;
        if (jdbc != null) {
            jdbc.update("UPDATE email_outbox SET status = ?, attempts = attempts + 1, last_error = ?, next_attempt_at = COALESCE(?, next_attempt_at) WHERE id = ?",
                    status.name(), message, retryAt == null ? null : Timestamp.from(retryAt), id);
            return;
        }
        memory.computeIfPresent(id, (k, e) -> new Entry(id, e.orderReference(), e.kind(), e.recipient(), status,
                e.attempts() + 1, message, e.createdAt(), retryAt == null ? e.nextAttemptAt() : retryAt, null, e.details()));
    }

    private Entry find(String orderReference, Kind kind) {
        return memory.values().stream().filter(e -> e.orderReference().equals(orderReference) && e.kind() == kind)
                .findFirst().orElse(null);
    }

    private static Entry entry(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getLong("id"), rs.getString("order_reference"), Kind.valueOf(rs.getString("kind")),
                rs.getString("recipient"), Status.valueOf(rs.getString("status")), rs.getInt("attempts"),
                rs.getString("last_error"), instant(rs, "created_at"), instant(rs, "next_attempt_at"), instant(rs, "sent_at"),
                rs.getString("details"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
