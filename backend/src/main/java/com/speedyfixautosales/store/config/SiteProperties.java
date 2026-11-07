package com.speedyfixautosales.store.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business details shown on the storefront. Values come from application.yml (prefix "site"). phone is the mobile (also
 * on WhatsApp); landline is the shop's landline.
 */
@ConfigurationProperties(prefix = "site")
public record SiteProperties(
        String name,
        String tagline,
        String phone,
        String landline,
        String whatsapp,
        String email,
        String companyName,
        String address,
        String videoUrl,
        String warranty,
        String shipsFrom,
        String couriers,
        String returns,
        String vatNumber,
        String companyNumber,
        List<String> corsAllowedOrigins) {

    public SiteProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
        videoUrl = videoUrl == null ? "" : videoUrl;
        landline = landline == null ? "" : landline;
    }
}
