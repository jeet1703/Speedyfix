package com.speedyfixautosales.store.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Listing photos, by key ("listings/{productId}/0-1600.jpg"). With R2 set up (R2Client) they go to the bucket,
 * which is what the live site needs: Render's disk is wiped on every restart. Every photo is also kept on disk
 * under PHOTOS_DIR (default ../storage, so `storage/` in the project when the backend runs from `backend/`),
 * which serves as a cache in front of R2.
 */
@Component
public class PhotoStorage {

    private final Path root;
    private final R2Client r2;

    public PhotoStorage(@Value("${photos.dir:../storage}") String dir, R2Client r2) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.r2 = r2;
    }

    public void put(String key, byte[] data) {
        Path target = resolve(key).orElseThrow(() -> new IllegalArgumentException("Bad photo key " + key));
        try {
            if (r2.enabled()) {
                r2.put(key, data, contentType(key));
            }
            write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn’t save photo " + key, e);
        }
    }

    /** The file for a key, on disk or fetched from R2 (and kept on disk). Keys that would step outside the storage folder ("../") are refused. */
    public Optional<Path> file(String key) {
        Optional<Path> path = resolve(key);
        if (path.isEmpty() || Files.isRegularFile(path.get()) || !r2.enabled()) {
            return path.filter(Files::isRegularFile);
        }
        try {
            Optional<byte[]> data = r2.get(key);
            if (data.isEmpty()) {
                return Optional.empty();
            }
            write(path.get(), data.get());
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn’t fetch photo " + key, e);
        }
    }

    public static String contentType(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith(".webp") ? "image/webp" : k.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private static void write(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
