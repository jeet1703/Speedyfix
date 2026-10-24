package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.common.NotFoundException;
import com.speedyfixautosales.store.partrequest.PartRequest;
import com.speedyfixautosales.store.partrequest.PartRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shoppers' part requests, for staff to quote. Contact details are only ever returned behind staff sign-in. */
@RestController
@RequestMapping("/api/admin/part-requests")
public class AdminPartRequestController {

    /** Types served as-is; anything else a browser claimed is sent as a download-only byte stream. */
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final PartRequestService requests;

    public AdminPartRequestController(PartRequestService requests) {
        this.requests = requests;
    }

    /** A request without its photo bytes; photoCount says how many to fetch. */
    public record Summary(String reference, Instant receivedAt, String maskedRegistration, String vehicle, String part,
                          String notes, String name, String phone, String email, PartRequest.ReplyBy replyBy,
                          PartRequest.Status status, int photoCount) {

        static Summary of(PartRequest r) {
            return new Summary(r.reference(), r.receivedAt(), r.maskedRegistration(), r.vehicle(), r.part(), r.notes(),
                    r.name(), r.phone(), r.email(), r.replyBy(), r.status(), r.photos().size());
        }
    }

    public record StatusChange(@NotNull(message = "Choose a status.") PartRequest.Status status) {
    }

    @GetMapping
    public List<Summary> all() {
        return requests.all().stream().map(Summary::of).toList();
    }

    @PatchMapping("/{reference}")
    public Summary setStatus(@PathVariable String reference, @Valid @RequestBody StatusChange change) {
        return Summary.of(requests.setStatus(reference, change.status()));
    }

    @GetMapping("/{reference}/photos/{index}")
    public ResponseEntity<byte[]> photo(@PathVariable String reference, @PathVariable int index) {
        List<PartRequest.Photo> photos = requests.get(reference).photos();
        if (index < 0 || index >= photos.size()) {
            throw new NotFoundException("That photo isn’t stored any more.");
        }
        PartRequest.Photo photo = photos.get(index);
        String type = IMAGE_TYPES.contains(photo.contentType()) ? photo.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(photo.data());
    }
}
