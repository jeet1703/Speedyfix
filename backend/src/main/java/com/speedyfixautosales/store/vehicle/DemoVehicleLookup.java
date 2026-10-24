package com.speedyfixautosales.store.vehicle;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Sample vehicles for the prototype. Plates AB12 CDE, VX16 ABC and JG17 XEE resolve; others don't. */
@Component
public class DemoVehicleLookup implements VehicleLookup {

    private final List<Vehicle> vehicles = List.of(
            new Vehicle("navara", "AB12CDE", "Nissan", "Navara D40", "2.5 dCi diesel", "YD25DDTi", 2010),
            new Vehicle("astra", "VX16ABC", "Vauxhall", "Astra K", "1.4 Turbo petrol", null, 2016),
            new Vehicle("xe", "JG17XEE", "Jaguar", "XE", "2.0 diesel", null, 2017),
            new Vehicle("lc80", null, "Toyota", "Land Cruiser 80", "4.5 petrol", "1FZ-FE", null),
            new Vehicle("impreza", null, "Subaru", "Impreza", "2.0 petrol", "EJ20", null),
            new Vehicle("r33", null, "Nissan", "Skyline R33", "2.5 turbo petrol", "RB25DET", null),
            new Vehicle("tundra", null, "Toyota", "Tundra", "5.7 V8 petrol", "3UR-FE", null));

    @Override
    public Optional<Vehicle> findByRegistration(String registration) {
        return vehicles.stream().filter(v -> registration.equals(v.registration())).findFirst();
    }

    @Override
    public Optional<Vehicle> findById(String id) {
        return vehicles.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    @Override
    public List<Vehicle> all() {
        return vehicles;
    }
}
