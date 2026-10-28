package com.speedyfixautosales.store.catalog;

/** Whether a part fits the shopper's saved vehicle. */
public enum FitStatus {
    /** The part is confirmed for the vehicle. */
    FITS,
    /**
     * Not confirmed, but the listing's title names this make and model, and its years and engine size (where given)
     * match. Staff check the part number before dispatch.
     */
    LIKELY_FITS,
    /** The part is confirmed for other vehicles, not this one. */
    DOES_NOT_FIT,
    /** No fitment data yet; staff confirm by OE number before dispatch. */
    UNCONFIRMED,
    /** The shopper has not added a vehicle. */
    NO_VEHICLE
}
