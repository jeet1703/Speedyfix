package com.speedyfixautosales.store.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Shopper accounts, created the first time someone signs in with Google or once they've confirmed their email address
 * with a code (email sign-in), plus each account's recent searches.
 * Kept in PostgreSQL with the "postgres" profile (JdbcCustomerService), otherwise in memory.
 */
public interface CustomerService {

    int MAX_SEARCHES = 10;

    enum SignInMethod { GOOGLE, EMAIL }

    record Customer(String id, String email, String name, String pictureUrl, Instant joinedAt,
                    Instant lastSignInAt, int signIns, SignInMethod signInMethod) {
    }

    /** An email-and-password account with its stored password hash. */
    record PasswordAccount(Customer customer, String passwordHash) {
    }

    /** Email accounts' ids start with this; Google accounts use Google's id. */
    String EMAIL_ID_PREFIX = "email-";

    /** Finds or creates the account for this Google identity, keeping its name and photo up to date. */
    Customer signIn(GoogleTokenVerifier.GoogleIdentity google);

    /** How the account using this address (ignoring case) signs in; empty if no account uses it. */
    Optional<SignInMethod> accountFor(String email);

    /**
     * Creates an email-and-password account, once the shopper has confirmed the address with a code.
     * @throws com.speedyfixautosales.store.common.ConflictException if an email account already uses the address
     */
    Customer createWithPassword(String email, String name, String passwordHash);

    /** The email-and-password account for this address (ignoring case). */
    Optional<PasswordAccount> passwordAccount(String email);

    /** Replaces an email account's password ("Forgot password"). */
    void setPassword(String customerId, String passwordHash);

    /** Notes a password sign-in: the time and the count. */
    Customer recordSignIn(String id);

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
