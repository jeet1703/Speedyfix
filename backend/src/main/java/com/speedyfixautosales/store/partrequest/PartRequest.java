package com.speedyfixautosales.store.partrequest;

import java.time.Instant;
import java.util.List;

/**
 * A shopper asking us to source a part. The plate is kept masked; staff work from the
 * vehicle it identified. Photos are held in memory for the prototype. Staff move it from NEW to QUOTED
 * once they've replied, and CLOSED when it's done with.
 */
public record PartRequest(
        String reference,
        Instant receivedAt,
        String maskedRegistration,
        String vehicle,
        String part,
        String notes,
        String name,
        String phone,
        String email,
        ReplyBy replyBy,
        List<Photo> photos,
        Status status) {

    public enum ReplyBy {
        WHATSAPP, PHONE, EMAIL
    }

    public enum Status {
        NEW, QUOTED, CLOSED
    }

    public PartRequest withPhotos(List<Photo> newPhotos) {
        return new PartRequest(reference, receivedAt, maskedRegistration, vehicle, part, notes, name, phone, email, replyBy,
                newPhotos, status);
    }

    public PartRequest withStatus(Status newStatus) {
        return new PartRequest(reference, receivedAt, maskedRegistration, vehicle, part, notes, name, phone, email, replyBy,
                photos, newStatus);
    }

    public record Photo(String filename, String contentType, byte[] data) {
    }
}
