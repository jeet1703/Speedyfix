package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.CatalogRepository;
import com.speedyfixautosales.store.catalog.MenuGroup;
import com.speedyfixautosales.store.config.SiteProperties;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The business details and Products menu that every page's header and footer need. */
@RestController
public class SiteController {

    private final SiteProperties site;
    private final CatalogRepository repository;

    public SiteController(SiteProperties site, CatalogRepository repository) {
        this.site = site;
        this.repository = repository;
    }

    public record SiteResponse(HomeResponse.Site site, List<MenuGroup> menu) {
    }

    @GetMapping("/api/site")
    public SiteResponse site() {
        return new SiteResponse(HomeResponse.Site.from(site), repository.menu());
    }
}
