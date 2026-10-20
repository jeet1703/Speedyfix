package com.speedyfix.store.partrequest;

import java.time.Instant;
import java.util.List;

/**
 * A shopper asking us to source a part. The plate is kept masked; staff work from the
 * vehicle it identified. Photos are held in memory for the prototype.
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
        List<Photo> photos) {

    public enum ReplyBy {
        WHATSAPP, PHONE, EMAIL
    }

    public record Photo(String filename, String contentType, byte[] data) {
    }
}
