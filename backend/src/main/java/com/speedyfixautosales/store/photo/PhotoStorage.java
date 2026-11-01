package com.speedyfixautosales.store.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Listing photos, by key ("listings/{productId}/0-1200.jpg"). With R2 set up (R2Client), the bucket is where photos
 * live: they're saved there and read from there, which is what the live site needs (Render's disk is wiped on every
 * restart, and the app's user can't write outside its own folder). Without R2 (development), photos are files under
 * PHOTOS_DIR (default ../storage). A file on disk is used first when there is one, so a development copy of the
 * photos works offline.
 */
@Component
public class PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorage.class);

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
                writeQuietly(target, data);
            } else {
                write(target, data);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn’t save photo " + key, e);
        }
    }

    /** The photo's bytes: from disk when there's a copy, else from R2. Empty if it doesn't exist or the key is unsafe. */
    public Optional<byte[]> read(String key) {
        Optional<Path> path = resolve(key);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (Files.isRegularFile(path.get())) {
                return Optional.of(Files.readAllBytes(path.get()));
            }
            return r2.enabled() ? r2.get(key) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn’t read photo " + key, e);
        }
    }

    public static String contentType(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith(".webp") ? "image/webp" : k.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    /** A local copy next to R2, for working offline in development; on the live server it's skipped if it can't be written. */
    private void writeQuietly(Path target, byte[] data) {
        try {
            write(target, data);
        } catch (IOException | RuntimeException e) {
            log.debug("No local copy of {}: {}", target, e.getMessage());
        }
    }

    private static void write(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Keys that would step outside the storage folder ("../") are refused. */
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
