package com.speedyfixautosales.store.partrequest;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.vehicle.Vehicle;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores part requests for staff. In memory for the prototype; production should email or
 * queue them to the sales team and keep them in the database.
 */
@Service
public class PartRequestService {

    static final int MAX_PHOTOS = 4;
    // Render's free instance has 512 MB, so cap what the in-memory store can hold.
    static final int MAX_STORED_REQUESTS = 500;
    static final long MAX_STORED_PHOTO_BYTES = 64L * 1024 * 1024;

    private final Map<String, PartRequest> requests = new ConcurrentHashMap<>();
    private final Deque<String> oldestFirst = new ArrayDeque<>();
    private long storedPhotoBytes;
    private final AtomicInteger sequence = new AtomicInteger(1000);
    private final VehicleService vehicles;

    public PartRequestService(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    public record Received(String reference, String vehicle, PartRequest.ReplyBy replyBy) {
    }

    public Received submit(String registration, String part, String notes, String name, String phone, String email,
                           PartRequest.ReplyBy replyBy, List<MultipartFile> photos) {
        if (replyBy == PartRequest.ReplyBy.EMAIL && (email == null || email.isBlank())) {
            throw new BadRequestException("Enter your email address so we can reply by email.");
        }
        if (replyBy != PartRequest.ReplyBy.EMAIL && (phone == null || phone.isBlank())) {
            throw new BadRequestException("Enter your phone or WhatsApp number so we can reply.");
        }
        Optional<Vehicle> vehicle = vehicles.identify(registration);
        PartRequest request = new PartRequest("PR-" + sequence.incrementAndGet(), Instant.now(),
                VehicleService.mask(registration),
                vehicle.map(v -> v.displayName() + ", " + v.engine()).orElse(null),
                part.trim(), blankToNull(notes), name.trim(), blankToNull(phone), blankToNull(email), replyBy,
                readPhotos(photos), PartRequest.Status.NEW);
        store(request);
        return new Received(request.reference(), request.vehicle(), replyBy);
    }

    /** Over the photo budget, the oldest requests lose their photos first; over the count, the oldest go. */
    private synchronized void store(PartRequest request) {
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

    /** Newest first, for the admin. */
    public List<PartRequest> all() {
        return requests.values().stream().sorted(Comparator.comparing(PartRequest::receivedAt).reversed()).toList();
    }

    public PartRequest get(String reference) {
        PartRequest request = reference == null ? null : requests.get(reference.toUpperCase(Locale.UK));
        if (request == null) {
            throw new NotFoundException("We couldn’t find that part request.");
        }
        return request;
    }

    /** Synchronized with store(), so a status change can't put back photos the budget just dropped. */
    public synchronized PartRequest setStatus(String reference, PartRequest.Status status) {
        PartRequest updated = get(reference).withStatus(status);
        requests.put(updated.reference(), updated);
        return updated;
    }

    private static long photoBytes(PartRequest r) {
        return r.photos().stream().mapToLong(p -> p.data().length).sum();
    }

    private static List<PartRequest.Photo> readPhotos(List<MultipartFile> files) {
        List<MultipartFile> present = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (present.size() > MAX_PHOTOS) {
            throw new BadRequestException("Attach up to " + MAX_PHOTOS + " photos.");
        }
        List<PartRequest.Photo> photos = new ArrayList<>();
        for (MultipartFile f : present) {
            String type = f.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new BadRequestException("Photos must be images, like JPG or PNG.");
            }
            try {
                photos.add(new PartRequest.Photo(f.getOriginalFilename(), type, f.getBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return photos;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
