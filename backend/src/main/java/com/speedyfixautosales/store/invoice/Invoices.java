package com.speedyfixautosales.store.invoice;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Invoice numbers, one per paid order, in the invoices table with the "postgres" profile (in memory without it).
 * Numbers run on from the highest so far, with no gaps, as HMRC expects.
 */
@Component
public class Invoices {

    static final long FIRST = 1001;

    /** Null without the database. */
    private final JdbcTemplate jdbc;
    private final Map<String, Invoice> memory = new ConcurrentHashMap<>();
    private final AtomicLong next = new AtomicLong(FIRST);

    public Invoices(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc.getIfAvailable();
    }

    public Optional<Invoice> find(String orderReference) {
        if (jdbc == null) {
            return Optional.ofNullable(memory.get(orderReference));
        }
        return jdbc.query("SELECT number, order_reference, issued_at FROM invoices WHERE order_reference = ?",
                (rs, row) -> new Invoice(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant()), orderReference)
                .stream().findFirst();
    }

    /** The order's invoice, numbered now if it has none yet. Safe to call again: the order keeps its number. */
    public synchronized Invoice issue(String orderReference, Instant issuedAt) {
        Optional<Invoice> existing = find(orderReference);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant at = issuedAt.truncatedTo(ChronoUnit.MICROS);
        if (jdbc == null) {
            Invoice invoice = new Invoice(next.getAndIncrement(), orderReference, at);
            memory.put(orderReference, invoice);
            return invoice;
        }
        jdbc.update("""
                INSERT INTO invoices (number, order_reference, issued_at)
                SELECT COALESCE(MAX(number), ?) + 1, ?, ? FROM invoices""", FIRST - 1, orderReference, Timestamp.from(at));
        return find(orderReference).orElseThrow();
    }
}
