package com.speedyfix.store.web;

import com.speedyfix.store.catalog.CatalogService;
import com.speedyfix.store.vehicle.Vehicle;
import com.speedyfix.store.vehicle.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicles;
    private final CatalogService catalog;

    public VehicleController(VehicleService vehicles, CatalogService catalog) {
        this.vehicles = vehicles;
        this.catalog = catalog;
    }

    public record LookupRequest(@NotBlank(message = "Enter your registration number.") @Size(max = 10) String registration) {
    }

    public record LookupResponse(Vehicle vehicle, long fittingParts) {
    }

    @PostMapping("/lookup")
    public LookupResponse lookUp(@Valid @RequestBody LookupRequest request) {
        Vehicle vehicle = vehicles.lookUp(request.registration());
        return new LookupResponse(vehicle, catalog.countFitting(vehicle.id()));
    }

    @GetMapping
    public List<Vehicle> all() {
        return vehicles.all();
    }
}
