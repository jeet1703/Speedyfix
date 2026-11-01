package com.speedyfixautosales.store.common;

/** A payment that didn't go through (HTTP 402); the message says what the shopper can do. */
public class PaymentFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentFailedException(String message) {
        super(message);
    }
}
