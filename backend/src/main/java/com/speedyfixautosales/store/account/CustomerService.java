package com.speedyfixautosales.store.account;

import java.time.Instant;
import java.util.List;

/**
 * Shopper accounts, created the first time someone signs in with Google, plus each account's recent searches.
 * Kept in PostgreSQL with the "postgres" profile (JdbcCustomerService), otherwise in memory.
 */
public interface CustomerService {

    int MAX_SEARCHES = 10;

    record Customer(String id, String email, String name, String pictureUrl, Instant joinedAt,
                    Instant lastSignInAt, int signIns) {
    }

    /** Finds or creates the account for this Google identity, keeping its name and photo up to date. */
    Customer signIn(GoogleTokenVerifier.GoogleIdentity google);

    /** Every account, most recently signed in first (for the admin). */
    List<Customer> all();

    /** @throws com.speedyfixautosales.store.common.NotFoundException if there's no such account */
    Customer get(String id);

    /** Most recent first. */
    List<String> searches(String customerId);

    /** Adds a search to the front, dropping an earlier copy of it (ignoring case) and anything past the last 10. */
    List<String> recordSearch(String customerId, String query);

    void clearSearches(String customerId);

    static String tidy(String query) {
        return query.trim().replaceAll("\\s+", " ");
    }
}
