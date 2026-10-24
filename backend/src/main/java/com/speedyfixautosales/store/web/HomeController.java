package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.CatalogService;
import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final SiteProperties site;
    private final CatalogRepository repository;
    private final CatalogService catalog;
    private final VehicleService vehicles;

    public HomeController(SiteProperties site, CatalogRepository repository, CatalogService catalog,
                          VehicleService vehicles) {
        this.site = site;
        this.repository = repository;
        this.catalog = catalog;
        this.vehicles = vehicles;
    }

    @GetMapping
    public HomeResponse home(@RequestParam(required = false) String vehicleId) {
        return new HomeResponse(
                HomeResponse.Site.from(site),
                repository.menu(),
                repository.slides(),
                repository.features(),
                catalog.popularCategories(),
                catalog.bestSelling(vehicleId),
                catalog.latest(vehicleId),
                List.of(),
                vehicles.findById(vehicleId).orElse(null));
    }
}
