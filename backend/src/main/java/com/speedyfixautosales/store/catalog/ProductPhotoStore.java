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

    int MAX_PER_PART = 4;

    record Photo(String id, String productId, String contentType, byte[] data) {
    }

    /** The part's uploaded photo ids, in order; the first is its main image. */
    List<String> idsFor(String productId);

    Optional<Photo> get(String photoId);

    /** Keeps only the listed photos for the part, then adds the new files. Returns the part's photo ids in order. */
    List<String> update(String productId, List<String> keepIds, List<MultipartFile> files);

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
