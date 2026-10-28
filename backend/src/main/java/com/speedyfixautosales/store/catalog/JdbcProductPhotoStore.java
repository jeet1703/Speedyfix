package com.speedyfixautosales.store.catalog;

import com.speedyfixautosales.store.common.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Staff photos in the database ("postgres" profile), so they survive redeploys until they move to Cloudflare R2.
 * Each is a product_photos row (source UPLOAD, r2_key "db:<id>") plus its file in product_photo_files. Uploads use
 * positions below zero, ahead of any photos downloaded from eBay, so the first upload is the listing's main image.
 * Photos downloaded from eBay aren't touched here.
 */
@Component
@Profile("postgres")
public class JdbcProductPhotoStore implements ProductPhotoStore {

    /** The r2_key prefix marking a photo whose file is in product_photo_files. */
    public static final String DB_KEY_PREFIX = "db:";
    private static final int FIRST_POSITION = -100;

    private final JdbcTemplate jdbc;

    public JdbcProductPhotoStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> idsFor(String productId) {
        return jdbc.queryForList("""
                SELECT id FROM product_photos WHERE product_id = ? AND source = 'UPLOAD' AND r2_key LIKE 'db:%'
                ORDER BY position""", String.class, productId);
    }

    @Override
    public Optional<Photo> get(String photoId) {
        return jdbc.query("""
                SELECT p.id, p.product_id, f.content_type, f.data FROM product_photos p
                JOIN product_photo_files f ON f.photo_id = p.id WHERE p.id = ?""",
                (rs, i) -> new Photo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBytes(4)), photoId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public List<String> update(String productId, List<String> keepIds, List<MultipartFile> files) {
        List<String> current = idsFor(productId);
        List<String> kept = current.stream().filter(id -> keepIds != null && keepIds.contains(id)).toList();
        List<MultipartFile> added = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (kept.size() + added.size() > MAX_PER_PART) {
            throw new BadRequestException("Add up to " + MAX_PER_PART + " photos per part.");
        }
        // Check every new file before changing anything.
        List<byte[]> data = added.stream().map(ProductPhotoStore::read).toList();
        List<String> types = data.stream().map(ProductPhotoStore::imageType).toList();

        current.stream().filter(id -> !kept.contains(id))
                .forEach(id -> jdbc.update("DELETE FROM product_photos WHERE id = ?", id));
        // Park the kept photos out of the way, so renumbering can't clash with the unique (product, position).
        for (String id : kept) {
            jdbc.update("UPDATE product_photos SET position = position - 1000 WHERE id = ?", id);
        }
        List<String> ids = new ArrayList<>(kept);
        for (int i = 0; i < data.size(); i++) {
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO product_photos (id, product_id, position, r2_key, source) VALUES (?, ?, ?, ?, 'UPLOAD')""",
                    id, productId, FIRST_POSITION - 1000 - ids.size(), DB_KEY_PREFIX + id);
            jdbc.update("INSERT INTO product_photo_files (photo_id, content_type, data) VALUES (?, ?, ?)", id, types.get(i), data.get(i));
            ids.add(id);
        }
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE product_photos SET position = ? WHERE id = ?", FIRST_POSITION + i, ids.get(i));
        }
        return List.copyOf(ids);
    }
}
