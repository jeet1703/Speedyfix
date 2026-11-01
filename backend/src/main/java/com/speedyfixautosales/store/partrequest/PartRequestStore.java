package com.speedyfixautosales.store.partrequest;

import java.util.List;
import java.util.Optional;

/**
 * Where part requests are kept: PostgreSQL with the "postgres" profile (JdbcPartRequestStore), otherwise memory.
 * Requests that come back from all() and find() may leave out photo bytes; photo() fetches one.
 */
public interface PartRequestStore {

    /** The next reference, like "PR-1001". */
    String nextReference();

    void add(PartRequest request);

    /** Newest first. */
    List<PartRequest> all();

    Optional<PartRequest> find(String reference);

    Optional<PartRequest.Photo> photo(String reference, int index);

    /** The updated request, or empty if there's no such request. */
    Optional<PartRequest> setStatus(String reference, PartRequest.Status status);
}
