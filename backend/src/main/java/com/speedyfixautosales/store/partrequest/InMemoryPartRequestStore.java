package com.speedyfixautosales.store.partrequest;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Part requests in memory, for running without a database (a restart clears them). */
@Component
@Profile("!postgres")
public class InMemoryPartRequestStore implements PartRequestStore {

    // Render's free instance has 512 MB, so cap what the in-memory store can hold.
    static final int MAX_STORED_REQUESTS = 500;
    static final long MAX_STORED_PHOTO_BYTES = 64L * 1024 * 1024;

    private final Map<String, PartRequest> requests = new ConcurrentHashMap<>();
    private final Deque<String> oldestFirst = new ArrayDeque<>();
    private long storedPhotoBytes;
    private final AtomicInteger sequence = new AtomicInteger(1000);

    @Override
    public String nextReference() {
        return "PR-" + sequence.incrementAndGet();
    }

    /** Over the photo budget, the oldest requests lose their photos first; over the count, the oldest go. */
    @Override
    public synchronized void add(PartRequest request) {
        requests.put(request.reference(), request);
        oldestFirst.addLast(request.reference());
        storedPhotoBytes += photoBytes(request);
        for (String ref : oldestFirst) {
            if (storedPhotoBytes <= MAX_STORED_PHOTO_BYTES) {
                break;
            }
            PartRequest old = requests.get(ref);
            if (old != null && !old.photos().isEmpty() && !ref.equals(request.reference())) {
                storedPhotoBytes -= photoBytes(old);
                requests.put(ref, old.withPhotos(List.of()));
            }
        }
        while (oldestFirst.size() > MAX_STORED_REQUESTS) {
            PartRequest removed = requests.remove(oldestFirst.removeFirst());
            if (removed != null) {
                storedPhotoBytes -= photoBytes(removed);
            }
        }
    }

    @Override
    public List<PartRequest> all() {
        return requests.values().stream().sorted(Comparator.comparing(PartRequest::receivedAt).reversed()).toList();
    }

    @Override
    public Optional<PartRequest> find(String reference) {
        return Optional.ofNullable(requests.get(reference));
    }

    @Override
    public Optional<PartRequest.Photo> photo(String reference, int index) {
        return find(reference).map(PartRequest::photos).filter(p -> index >= 0 && index < p.size()).map(p -> p.get(index));
    }

    /** Synchronized with add(), so a status change can't put back photos the budget just dropped. */
    @Override
    public synchronized Optional<PartRequest> setStatus(String reference, PartRequest.Status status) {
        return find(reference).map(r -> {
            PartRequest updated = r.withStatus(status);
            requests.put(updated.reference(), updated);
            return updated;
        });
    }

    private static long photoBytes(PartRequest r) {
        return r.photos().stream().mapToLong(p -> p.data().length).sum();
    }
}
