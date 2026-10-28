package com.speedyfixautosales.store.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Turns a photo key into the address shoppers load it from. With IMAGES_BASE_URL set (Cloudflare R2's
 * images.<domain> once it's live) that's used as-is; otherwise photos come from this server's /api/images.
 * The shop runs on another host (GitHub Pages), so on Render the address must be absolute: the catalogue is
 * cached from a background load with no request to take the host from, so RENDER_EXTERNAL_URL supplies it.
 */
@Component
public class PhotoUrls {

    private final String baseUrl;
    private final String server;

    public PhotoUrls(@Value("${photos.base-url:}") String baseUrl, @Value("${RENDER_EXTERNAL_URL:}") String serverUrl) {
        this.server = serverUrl.replaceAll("/+$", "");
        String images = baseUrl.replaceAll("/+$", "");
        this.baseUrl = !images.isEmpty() ? images : server.isEmpty() ? "" : server + "/api/images";
    }

    /** An address on this server, such as /api/product-photos/{id}, made absolute the same way as photos. */
    public String server(String path) {
        if (!server.isEmpty()) {
            return server + path;
        }
        if (RequestContextHolder.getRequestAttributes() != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
        }
        return path;
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
