package com.speedyfixautosales.store.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Turns a photo key into the address shoppers load it from. With IMAGES_BASE_URL set (Cloudflare R2's
 * images.<domain> once it's live) that's used as-is; otherwise photos come from this server's /api/images.
 */
@Component
public class PhotoUrls {

    private final String baseUrl;

    public PhotoUrls(@Value("${photos.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String url(String key) {
        if (!baseUrl.isEmpty()) {
            return baseUrl + "/" + key;
        }
        // In a request, use its own host, so the address works through the local dev proxy and on any server.
        if (RequestContextHolder.getRequestAttributes() != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/images/").path(key).toUriString();
        }
        return "/api/images/" + key;
    }
}
