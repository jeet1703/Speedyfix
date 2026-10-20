package com.speedyfix.store.vehicle;

import java.util.List;
import java.util.Optional;

/**
 * Source of vehicle data. The demo implementation knows a handful of sample plates;
 * production swaps in a client for a licensed UK VRM provider (see README).
 */
public interface VehicleLookup {

    /** @param registration normalised registration: upper case, no spaces */
    Optional<Vehicle> findByRegistration(String registration);

    Optional<Vehicle> findById(String id);

    List<Vehicle> all();
}
