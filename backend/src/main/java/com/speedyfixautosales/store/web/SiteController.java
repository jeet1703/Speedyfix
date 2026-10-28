package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.MenuGroup;
import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The business details and Products menu that every page's header and footer need. */
@RestController
public class SiteController {

    private final SiteProperties site;
    private final CatalogRepository repository;
    private final String googleClientId;
    private final VehicleService vehicles;

    public SiteController(SiteProperties site, CatalogRepository repository,
                          @Value("${account.google-client-id:}") String googleClientId, VehicleService vehicles) {
        this.site = site;
        this.repository = repository;
        this.googleClientId = googleClientId.trim();
        this.vehicles = vehicles;
    }

    /**
     * googleClientId is public (Google's button needs it in the browser); empty until Google sign-in is set up.
     * demoPlates is true until DVSA is connected, while only the sample plates resolve.
     */
    public record SiteResponse(HomeResponse.Site site, List<MenuGroup> menu, String googleClientId, boolean demoPlates) {
    }

    @GetMapping("/api/site")
    public SiteResponse site() {
        return new SiteResponse(HomeResponse.Site.from(site), repository.menu(), googleClientId, vehicles.demoPlates());
    }
}
