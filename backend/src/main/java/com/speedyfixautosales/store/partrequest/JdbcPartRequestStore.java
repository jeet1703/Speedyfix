package com.speedyfixautosales.store.partrequest;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Part requests in PostgreSQL (part_requests, part_request_photos). Photo bytes stay in the database and are only
 * read one at a time, when staff open a photo.
 */
@Component
@Profile("postgres")
public class JdbcPartRequestStore implements PartRequestStore {

    private final JdbcTemplate jdbc;

    public JdbcPartRequestStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String nextReference() {
        return "PR-" + jdbc.queryForObject("SELECT nextval('part_request_number')", Long.class);
    }

    @Override
    @Transactional
    public void add(PartRequest r) {
        jdbc.update("""
                INSERT INTO part_requests (reference, received_at, masked_registration, vehicle, part, notes, name, phone, email,
                    reply_by, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                r.reference(), Timestamp.from(r.receivedAt()), r.maskedRegistration(), r.vehicle(), r.part(), r.notes(),
                r.name(), r.phone(), r.email(), r.replyBy().name(), r.status().name());
        int position = 0;
        for (PartRequest.Photo p : r.photos()) {
            jdbc.update("INSERT INTO part_request_photos (reference, position, filename, content_type, data) VALUES (?, ?, ?, ?, ?)",
                    r.reference(), position++, cut(p.filename(), 200), cut(p.contentType(), 50), p.data());
        }
    }

    @Override
    public List<PartRequest> all() {
        return load("");
    }

    @Override
    public Optional<PartRequest> find(String reference) {
        return load("WHERE r.reference = ?", reference).stream().findFirst();
    }

    @Override
    public Optional<PartRequest.Photo> photo(String reference, int index) {
        return jdbc.query("SELECT filename, content_type, data FROM part_request_photos WHERE reference = ? AND position = ? AND data IS NOT NULL",
                (rs, row) -> new PartRequest.Photo(rs.getString("filename"), rs.getString("content_type"), rs.getBytes("data")),
                reference, index).stream().findFirst();
    }

    @Override
    public Optional<PartRequest> setStatus(String reference, PartRequest.Status status) {
        if (jdbc.update("UPDATE part_requests SET status = ? WHERE reference = ?", status.name(), reference) == 0) {
            return Optional.empty();
        }
        return find(reference);
    }

    /** Photos come back without their bytes. */
    private List<PartRequest> load(String where, Object... args) {
        Map<String, List<PartRequest.Photo>> photos = new HashMap<>();
        jdbc.query("SELECT p.reference, p.filename, p.content_type FROM part_request_photos p JOIN part_requests r ON r.reference = p.reference "
                        + where + " ORDER BY p.reference, p.position",
                rs -> {
                    photos.computeIfAbsent(rs.getString("reference"), k -> new ArrayList<>())
                            .add(new PartRequest.Photo(rs.getString("filename"), rs.getString("content_type"), null));
                }, args);
        return jdbc.query("SELECT r.* FROM part_requests r " + where + " ORDER BY r.received_at DESC",
                (rs, row) -> new PartRequest(rs.getString("reference"), rs.getTimestamp("received_at").toInstant(),
                        rs.getString("masked_registration"), rs.getString("vehicle"), rs.getString("part"), rs.getString("notes"),
                        rs.getString("name"), rs.getString("phone"), rs.getString("email"),
                        PartRequest.ReplyBy.valueOf(rs.getString("reply_by")),
                        List.copyOf(photos.getOrDefault(rs.getString("reference"), List.of())),
                        PartRequest.Status.valueOf(rs.getString("status"))),
                args);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
