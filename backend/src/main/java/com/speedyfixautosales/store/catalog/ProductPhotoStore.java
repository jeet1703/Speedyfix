package com.speedyfixautosales.store.catalog;

import com.speedyfixautosales.store.common.BadRequestException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photos staff upload for a part. In memory for the prototype, so they are lost on every redeploy;
 * production should keep them in object storage. Only JPEG, PNG and WebP are accepted, checked by
 * their first bytes, so nothing else can be served back from our domain.
 */
@Component
public class ProductPhotoStore {

    static final int MAX_PER_PART = 4;
    // Render's free instance has 512 MB; part-request photos have their own 64 MB budget.
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;

    public record Photo(String id, String productId, String contentType, byte[] data) {
    }

    private final Map<String, Photo> photos = new HashMap<>();
    private final Map<String, List<String>> byProduct = new HashMap<>();
    private long totalBytes;

    public synchronized List<String> idsFor(String productId) {
        return List.copyOf(byProduct.getOrDefault(productId, List.of()));
    }

    public synchronized Optional<Photo> get(String photoId) {
        return Optional.ofNullable(photos.get(photoId));
    }

    /** Keeps only the listed photos for the part, then adds the new files. Returns the part's photo ids in order. */
    public synchronized List<String> update(String productId, List<String> keepIds, List<MultipartFile> files) {
        List<String> current = byProduct.getOrDefault(productId, List.of());
        List<String> kept = current.stream().filter(id -> keepIds != null && keepIds.contains(id)).toList();
        List<MultipartFile> added = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (kept.size() + added.size() > MAX_PER_PART) {
            throw new BadRequestException("Add up to " + MAX_PER_PART + " photos per part.");
        }
        List<Photo> incoming = new ArrayList<>();
        long incomingBytes = 0;
        for (MultipartFile f : added) {
            byte[] data = read(f);
            incoming.add(new Photo(UUID.randomUUID().toString(), productId, imageType(data), data));
            incomingBytes += data.length;
        }
        long removedBytes = current.stream().filter(id -> !kept.contains(id)).mapToLong(id -> photos.get(id).data().length).sum();
        if (totalBytes - removedBytes + incomingBytes > MAX_TOTAL_BYTES) {
            throw new BadRequestException("Photo storage is full on this prototype server. Use smaller photos or remove some first.");
        }
        current.stream().filter(id -> !kept.contains(id)).forEach(photos::remove);
        List<String> ids = new ArrayList<>(kept);
        for (Photo p : incoming) {
            photos.put(p.id(), p);
            ids.add(p.id());
        }
        totalBytes += incomingBytes - removedBytes;
        byProduct.put(productId, List.copyOf(ids));
        return List.copyOf(ids);
    }

    private static byte[] read(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The real type from the file's signature; the browser-supplied content type isn't trusted. */
    static String imageType(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        throw new BadRequestException("Photos must be JPG, PNG or WebP images.");
    }
}
