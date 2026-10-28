package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.common.NotFoundException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Shopper accounts, created the first time someone signs in with Google, plus each account's recent searches.
 * Held in memory like the rest of the prototype, so a redeploy clears them.
 */
@Service
public class CustomerService {

    static final int MAX_SEARCHES = 10;

    public record Customer(String id, String email, String name, String pictureUrl, Instant joinedAt,
                           Instant lastSignInAt, int signIns) {
    }

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> searches = new ConcurrentHashMap<>();

    /** Finds or creates the account for this Google identity, keeping its name and photo up to date. */
    public Customer signIn(GoogleTokenVerifier.GoogleIdentity google) {
        Instant now = Instant.now();
        return customers.compute(google.sub(), (id, existing) -> new Customer(id, google.email(), google.name(),
                google.pictureUrl(), existing != null ? existing.joinedAt() : now, now,
                existing != null ? existing.signIns() + 1 : 1));
    }

    /** Every account, most recently signed in first (for the admin). */
    public List<Customer> all() {
        return customers.values().stream().sorted(Comparator.comparing(Customer::lastSignInAt).reversed()).toList();
    }

    public Customer get(String id) {
        Customer c = id == null ? null : customers.get(id);
        if (c == null) {
            throw new NotFoundException("We couldn’t find your account. Sign in again.");
        }
        return c;
    }

    /** Most recent first. */
    public List<String> searches(String customerId) {
        Deque<String> list = searches.get(customerId);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    /** Adds a search to the front, dropping an earlier copy of it (ignoring case) and anything past the last 10. */
    public List<String> recordSearch(String customerId, String query) {
        String q = query.trim().replaceAll("\\s+", " ");
        Deque<String> list = searches.computeIfAbsent(customerId, id -> new ArrayDeque<>());
        synchronized (list) {
            list.removeIf(s -> s.toLowerCase(Locale.UK).equals(q.toLowerCase(Locale.UK)));
            list.addFirst(q);
            while (list.size() > MAX_SEARCHES) {
                list.removeLast();
            }
            return List.copyOf(list);
        }
    }

    public void clearSearches(String customerId) {
        searches.remove(customerId);
    }
}
