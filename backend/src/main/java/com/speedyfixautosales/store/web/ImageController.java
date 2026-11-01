package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.Images;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.photo.PhotoStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves listing photos at /api/images/{key} (from R2, or the local folder in development; see PhotoStorage), plus the
 * "photo coming soon" placeholder.
 */
@RestController
public class ImageController {

    private final PhotoStorage storage;

    public ImageController(PhotoStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/images/**")
    public ResponseEntity<Resource> image(HttpServletRequest request) {
        String key = request.getRequestURI().substring(request.getRequestURI().indexOf("/api/images/") + "/api/images/".length());
        if (key.equals(Images.PLACEHOLDER_KEY)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new ClassPathResource("static/placeholder.svg"));
        }
        byte[] photo = storage.read(key).orElseThrow(() -> new NotFoundException("No photo at " + key + "."));
        // A key never changes content (a replaced photo gets a new key), so browsers and Cloudflare can keep it a year.
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(PhotoStorage.contentType(key)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(photo));
    }
}
