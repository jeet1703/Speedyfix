package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.ProductPhotoStore;
import com.speedyfixautosales.store.common.NotFoundException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Serves the photos staff upload for parts. Photo ids are random and a photo never changes, so it caches well. */
@RestController
public class ProductPhotoController {

    private final ProductPhotoStore photos;

    public ProductPhotoController(ProductPhotoStore photos) {
        this.photos = photos;
    }

    @GetMapping("/api/product-photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        ProductPhotoStore.Photo photo = photos.get(id).orElseThrow(() -> new NotFoundException("No photo with id " + id + "."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.data());
    }
}
