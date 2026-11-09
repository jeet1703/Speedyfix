package com.speedyfixautosales.store.catalog;

import com.speedyfixautosales.store.common.BadRequestException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photos staff upload for a part: in memory for the sample catalogue (InMemoryProductPhotoStore), in the database
 * with the "postgres" profile (JdbcProductPhotoStore). Only JPEG, PNG and WebP are accepted, checked by their first
 * bytes, so nothing else can be served back from our domain.
 */
public interface ProductPhotoStore {

    /** As many as eBay allows on a listing. */
    int MAX_PER_PART = 24;

    record Photo(String id, String productId, String contentType, byte[] data) {
    }

    /** The part's uploaded photo ids, in order; the first is its main image. */
    List<String> idsFor(String productId);

    Optional<Photo> get(String photoId);

    /** Keeps only the listed photos for the part, then adds the new files. Returns the part's photo ids in order. */
    List<String> update(String productId, List<String> keepIds, List<MultipartFile> files);

    /** A photo the part came with from its eBay listing: shown after the uploaded ones. */
    record Imported(String id, String url, String thumbnailUrl) {
    }

    /** The part's photos from its eBay listing, in order (none in the sample catalogue). */
    default List<Imported> imported(String productId) {
        return List.of();
    }

    /** Takes the given photos from the eBay listing off the part; ids that aren't its imported photos are ignored. */
    default void removeImported(String productId, List<String> photoIds) {
    }

    /** All the part's photos, uploaded and from eBay, in the order the shop shows them (the first is the main image). */
    default List<String> order(String productId) {
        return idsFor(productId);
    }

    /**
     * Puts the part's photos in the given order, so the first is the main image. Ids that aren't the part's photos are
     * ignored; its photos not listed keep their order, after the listed ones.
     */
    default void reorder(String productId, List<String> photoIds) {
    }

    static byte[] read(MultipartFile f) {
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
