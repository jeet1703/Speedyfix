package com.speedyfixautosales.store.common;

/** Sign-in is missing or has expired (401). */
public class UnauthorizedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(message);
    }
}
