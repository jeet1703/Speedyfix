package com.speedyfix.store.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Business details shown on the storefront. Values come from application.yml (prefix "site"). */
@ConfigurationProperties(prefix = "site")
public record SiteProperties(
        String name,
        String tagline,
        String phone,
        String whatsapp,
        String email,
        String companyName,
        String address,
        String videoUrl,
        String warranty,
        String shipsFrom,
        List<String> corsAllowedOrigins) {

    public SiteProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
        videoUrl = videoUrl == null ? "" : videoUrl;
    }
}
