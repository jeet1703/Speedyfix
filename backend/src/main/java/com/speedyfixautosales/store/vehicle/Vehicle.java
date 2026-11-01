package com.speedyfixautosales.store.vehicle;

/** A vehicle identified from a registration, or a compatibility row picked by make and model. year is the vehicle's year; for a compatibility row (VehicleRange) it's the first year and yearTo the last. */
public record Vehicle(String id, String registration, String make, String model, String engine, String engineCode,
                      Integer year, Integer yearTo) {

    public Vehicle(String id, String registration, String make, String model, String engine, String engineCode, Integer year) {
        this(id, registration, make, model, engine, engineCode, year, null);
    }

    public String displayName() {
        return make + " " + model;
    }
}
