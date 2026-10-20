package com.speedyfix.store.partrequest;

import com.speedyfix.store.common.BadRequestException;
import com.speedyfix.store.vehicle.Vehicle;
import com.speedyfix.store.vehicle.VehicleService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    private final Map<String, PartRequest> requests = new ConcurrentHashMap<>();
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
                readPhotos(photos));
        requests.put(request.reference(), request);
        return new Received(request.reference(), request.vehicle(), replyBy);
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
