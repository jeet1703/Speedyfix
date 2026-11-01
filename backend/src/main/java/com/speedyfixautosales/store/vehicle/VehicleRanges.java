package com.speedyfixautosales.store.vehicle;

import java.util.List;
import java.util.Optional;

/** Where compatibility rows are kept: the vehicles table with the "postgres" profile, otherwise memory. */
public interface VehicleRanges {

    /** Adds the row if it isn't there already (same id), and returns it. */
    VehicleRange save(VehicleRange range);

    Optional<VehicleRange> find(String id);

    List<VehicleRange> all();
}
