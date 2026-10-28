package com.speedyfixautosales.store.vehicle;

import java.util.List;
import java.util.Optional;

/**
 * Source of vehicle data: DvsaVehicleLookup (DVSA's MOT History API) once its credentials are set, otherwise the
 * demo list of sample plates (see VehicleLookupConfig).
 */
public interface VehicleLookup {

    /** @param registration normalised registration: upper case, no spaces */
    Optional<Vehicle> findByRegistration(String registration);

    Optional<Vehicle> findById(String id);

    List<Vehicle> all();

    /** True for the sample data, so messages can suggest the demo plates. */
    default boolean demo() {
        return true;
    }
}
