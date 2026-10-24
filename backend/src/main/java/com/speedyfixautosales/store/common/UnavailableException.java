package com.speedyfixautosales.store.common;

/** A feature that isn't set up on this server (503). */
public class UnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnavailableException(String message) {
        super(message);
    }
}
