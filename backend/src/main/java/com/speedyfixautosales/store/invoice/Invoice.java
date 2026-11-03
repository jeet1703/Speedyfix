package com.speedyfixautosales.store.invoice;

import java.time.Instant;

/** A paid order's VAT invoice. issuedAt is the time of supply (when the order was paid). */
public record Invoice(long number, String orderReference, Instant issuedAt) {

    /** "INV-001001". */
    public String label() {
        return "INV-%06d".formatted(number);
    }
}
