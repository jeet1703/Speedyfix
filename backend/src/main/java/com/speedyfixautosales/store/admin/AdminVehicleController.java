package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.vehicle.Vehicle;
import com.speedyfixautosales.store.vehicle.VehicleCatalogue;
import com.speedyfixautosales.store.vehicle.VehicleRange;
import com.speedyfixautosales.store.vehicle.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The listing editor's vehicle picker: eBay UK's catalogue step by step (makes, then models, years and engines), and
 * adding a compatibility row from it. Every route needs a staff session (AdminWebConfig).
 */
@RestController
@RequestMapping("/api/admin/vehicle-catalogue")
public class AdminVehicleController {

    private final VehicleCatalogue catalogue;
    private final VehicleService vehicles;

    public AdminVehicleController(VehicleCatalogue catalogue, VehicleService vehicles) {
        this.catalogue = catalogue;
        this.vehicles = vehicles;
    }

    @GetMapping("/makes")
    public List<String> makes() {
        return catalogue.makes();
    }

    @GetMapping("/models")
    public List<String> models(@RequestParam String make) {
        return catalogue.models(make);
    }

    @GetMapping("/years")
    public List<String> years(@RequestParam String make, @RequestParam String model) {
        return catalogue.years(make, model);
    }

    @GetMapping("/engines")
    public List<String> engines(@RequestParam String make, @RequestParam String model, @RequestParam int yearFrom, @RequestParam int yearTo) {
        return catalogue.engines(make, model, yearFrom, yearTo);
    }

    public record NewRange(
            @NotBlank(message = "Choose the make.") @Size(max = 60) String make,
            @NotBlank(message = "Choose the model.") @Size(max = 100) String model,
            @NotNull(message = "Choose the first year.") @Min(1900) @Max(2100) Integer yearFrom,
            @NotNull(message = "Choose the last year.") @Min(1900) @Max(2100) Integer yearTo,
            /** eBay's engine name, or blank for all engines. */
            @Size(max = 100) List<@Size(max = 100) String> engines) {
    }

    /** Adds one row per chosen engine (or one for all engines) and returns them, ready to tick on the listing. */
    @PostMapping
    public List<AdminCatalogService.Fitment> add(@Valid @RequestBody NewRange r) {
        if (Math.abs(r.yearTo() - r.yearFrom()) > 60) {
            throw new BadRequestException("Choose a year range of up to 60 years.");
        }
        List<String> engines = r.engines() == null ? List.of() : r.engines().stream().filter(e -> e != null && !e.isBlank()).distinct().toList();
        List<String> chosen = engines.isEmpty() ? java.util.Collections.singletonList(null) : engines;
        return chosen.stream().map(engine -> {
            String fuel = engine == null ? null : catalogue.fuel(r.make(), r.model(), Math.min(r.yearFrom(), r.yearTo()), engine);
            Vehicle v = vehicles.addRange(VehicleRange.of(r.make(), r.model(), r.yearFrom(), r.yearTo(), engine, fuel));
            return AdminCatalogService.fitment(v, "Picked from eBay’s vehicle list", AdminCatalogService.Confidence.HIGH);
        }).toList();
    }
}
