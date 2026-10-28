package com.speedyfixautosales.store.catalog;

import com.speedyfixautosales.store.common.BadRequestException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Staff photos held in memory, for the built-in sample catalogue (no "postgres" profile), so they are lost on
 * every redeploy. With the database on, JdbcProductPhotoStore keeps them instead.
 */
@Component
@Profile("!postgres")
public class InMemoryProductPhotoStore implements ProductPhotoStore {

    // Render's free instance has 512 MB; part-request photos have their own 64 MB budget.
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;

    private final Map<String, Photo> photos = new HashMap<>();
    private final Map<String, List<String>> byProduct = new HashMap<>();
    private long totalBytes;

    @Override
    public synchronized List<String> idsFor(String productId) {
        return List.copyOf(byProduct.getOrDefault(productId, List.of()));
    }

    @Override
    public synchronized Optional<Photo> get(String photoId) {
        return Optional.ofNullable(photos.get(photoId));
    }

    @Override
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
            byte[] data = ProductPhotoStore.read(f);
            incoming.add(new Photo(UUID.randomUUID().toString(), productId, ProductPhotoStore.imageType(data), data));
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
}
