package com.speedyfixautosales.store.catalog;

/** Whether a part fits the shopper's saved vehicle. */
public enum FitStatus {
    /** The part is confirmed for the vehicle. */
    FITS,
    /** The part is confirmed for other vehicles, not this one. */
    DOES_NOT_FIT,
    /** No fitment data yet; staff confirm by OE number before dispatch. */
    UNCONFIRMED,
    /** The shopper has not added a vehicle. */
    NO_VEHICLE
}
