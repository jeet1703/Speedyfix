package com.speedyfixautosales.store.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Listing photos on disk, under PHOTOS_DIR (default ../storage, so `storage/` in the project when the backend runs
 * from `backend/`). Keys look like "listings/{productId}/0-1600.jpg"; the same keys move unchanged to Cloudflare R2.
 */
@Component
public class PhotoStorage {

    private final Path root;

    public PhotoStorage(@Value("${photos.dir:../storage}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public void put(String key, byte[] data) {
        Path target = resolve(key).orElseThrow(() -> new IllegalArgumentException("Bad photo key " + key));
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn’t save photo " + key, e);
        }
    }

    /** The file for a key, if it exists. Keys that would step outside the storage folder ("../") are refused. */
    public Optional<Path> file(String key) {
        return resolve(key).filter(Files::isRegularFile);
    }

    private Optional<Path> resolve(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Path p = root.resolve(key).normalize();
        return p.startsWith(root) && !p.equals(root) ? Optional.of(p) : Optional.empty();
    }

    public Path root() {
        return root;
    }
}
