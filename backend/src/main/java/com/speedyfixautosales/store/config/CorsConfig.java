package com.speedyfixautosales.store.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the React dev server (and any origins listed in site.cors-allowed-origins) call /api. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final SiteProperties site;

    public CorsConfig(SiteProperties site) {
        this.site = site;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(site.corsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*");
    }
}
