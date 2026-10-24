package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.vehicle.LookupLog;
import com.speedyfixautosales.store.vehicle.Vehicle;
import com.speedyfixautosales.store.vehicle.VehicleService;
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
    private final LookupLog log;

    public VehicleController(VehicleService vehicles, CatalogService catalog, LookupLog log) {
        this.vehicles = vehicles;
        this.catalog = catalog;
        this.log = log;
    }

    public record LookupRequest(@NotBlank(message = "Enter your registration number.") @Size(max = 10) String registration) {
    }

    public record LookupResponse(Vehicle vehicle, long fittingParts) {
    }

    @PostMapping("/lookup")
    public LookupResponse lookUp(@Valid @RequestBody LookupRequest request) {
        Vehicle vehicle;
        try {
            vehicle = vehicles.lookUp(request.registration());
        } catch (NotFoundException e) {
            log.record(null, false);
            throw e;
        }
        long fitting = catalog.countFitting(vehicle.id());
        log.record(vehicle.id(), fitting > 0);
        return new LookupResponse(vehicle, fitting);
    }

    @GetMapping
    public List<Vehicle> all() {
        return vehicles.all();
    }
}
