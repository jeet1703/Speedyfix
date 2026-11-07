package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.vehicle.CompatibilityTable;
import com.speedyfixautosales.store.vehicle.FitmentSuggester;
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
 * adding a compatibility row from it, or every row of a compatibility table pasted from an eBay listing. Every route needs a staff session (AdminWebConfig).
 */
@RestController
@RequestMapping("/api/admin/vehicle-catalogue")
public class AdminVehicleController {

    private final VehicleCatalogue catalogue;
    private final VehicleService vehicles;
    private final FitmentSuggester suggester;

    public AdminVehicleController(VehicleCatalogue catalogue, VehicleService vehicles, FitmentSuggester suggester) {
        this.catalogue = catalogue;
        this.vehicles = vehicles;
        this.suggester = suggester;
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

    /** The engines to choose from, each with its size, fuel and power spelled out ("1.5L diesel, 110HP"). */
    @GetMapping("/engines")
    public List<VehicleCatalogue.EngineChoice> engines(@RequestParam String make, @RequestParam String model,
                                                      @RequestParam int yearFrom, @RequestParam int yearTo) {
        return catalogue.engineChoices(make, model, yearFrom, yearTo);
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
            return AdminCatalogService.fitment(v, "Picked from the vehicle list", AdminCatalogService.Confidence.HIGH);
        }).toList();
    }

    /** eBay's table as copied from a listing page (CompatibilityTable); up to about 2,000 vehicle rows at a time. */
    public record Pasted(@NotBlank(message = "Paste the compatibility table from the eBay listing.") @Size(max = 400_000) String text) {
    }

    /** Adds a row per make, model, engine and run of years in the pasted table, and returns them, ready to tick. */
    @PostMapping("/pasted")
    public List<AdminCatalogService.Fitment> pasted(@Valid @RequestBody Pasted p) {
        List<VehicleRange> ranges = CompatibilityTable.ranges(CompatibilityTable.parse(p.text()));
        if (ranges.isEmpty()) {
            throw new BadRequestException("No vehicles found. On the eBay listing, open Compatibility, select the table "
                    + "from Make to the last row, copy it and paste it here.");
        }
        if (ranges.size() > ProductForm.MAX_VEHICLES) {
            throw new BadRequestException("That table has " + ranges.size() + " vehicles; a part can list up to "
                    + ProductForm.MAX_VEHICLES + ". Paste fewer pages.");
        }
        return ranges.stream()
                .map(r -> AdminCatalogService.fitment(vehicles.addRange(r), "Pasted from eBay", AdminCatalogService.Confidence.HIGH))
                .toList();
    }

    /** The part as staff have entered it so far: Gemini works from these. */
    public record SuggestRequest(
            @NotBlank(message = "Enter the title first.") @Size(max = 200) String title,
            @Size(max = 40) String partNumber,
            @Size(max = 20) String engineCode,
            @Size(max = 60) String brand,
            @Size(max = 100) String category,
            @Size(max = 2000) String notes) {
    }

    /** AI suggestions, matched to the vehicle list, and the suggestions that couldn't be matched. */
    public record Suggested(List<AdminCatalogService.Fitment> vehicles, List<String> notMatched) {
    }

    /** Gemini's suggestions for the part, as rows to tick (low confidence: AI can be wrong). */
    @PostMapping("/suggest")
    public Suggested suggest(@Valid @RequestBody SuggestRequest r) {
        FitmentSuggester.Result result = suggester.suggest(
                new FitmentSuggester.Part(r.title(), r.partNumber(), r.engineCode(), r.brand(), r.category(), r.notes()));
        return new Suggested(result.ranges().stream()
                .map(range -> AdminCatalogService.fitment(vehicles.addRange(range), "AI suggestion", AdminCatalogService.Confidence.LOW))
                .toList(), result.notMatched());
    }
}
