package com.speedyfixautosales.store.partrequest;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.vehicle.Vehicle;
import com.speedyfixautosales.store.vehicle.VehicleService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Takes part requests from shoppers and keeps them for staff in a PartRequestStore (the database with "postgres"). */
@Service
public class PartRequestService {

    static final int MAX_PHOTOS = 4;

    private final PartRequestStore store;
    private final VehicleService vehicles;

    public PartRequestService(PartRequestStore store, VehicleService vehicles) {
        this.store = store;
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
        List<PartRequest.Photo> read = readPhotos(photos);
        Optional<Vehicle> vehicle = vehicles.identify(registration);
        PartRequest request = new PartRequest(store.nextReference(), Instant.now(),
                VehicleService.mask(registration),
                vehicle.map(v -> v.displayName() + ", " + v.engine()).orElse(null),
                part.trim(), blankToNull(notes), name.trim(), blankToNull(phone), blankToNull(email), replyBy,
                read, PartRequest.Status.NEW);
        store.add(request);
        return new Received(request.reference(), request.vehicle(), replyBy);
    }

    /** Newest first, for the admin. Photo bytes may be left out: use photo() for those. */
    public List<PartRequest> all() {
        return store.all();
    }

    public PartRequest get(String reference) {
        return store.find(normalise(reference)).orElseThrow(PartRequestService::notFound);
    }

    public Optional<PartRequest.Photo> photo(String reference, int index) {
        return store.photo(normalise(reference), index);
    }

    public PartRequest setStatus(String reference, PartRequest.Status status) {
        return store.setStatus(normalise(reference), status).orElseThrow(PartRequestService::notFound);
    }

    private static String normalise(String reference) {
        return reference == null ? "" : reference.trim().toUpperCase(Locale.UK);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("We couldn’t find that part request.");
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
