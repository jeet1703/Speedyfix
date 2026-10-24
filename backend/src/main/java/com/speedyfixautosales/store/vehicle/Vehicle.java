package com.speedyfixautosales.store.vehicle;

/** A vehicle identified from a registration (or picked by make and model). */
public record Vehicle(String id, String registration, String make, String model, String engine, String engineCode,
                      Integer year) {

    public String displayName() {
        return make + " " + model;
    }
}
