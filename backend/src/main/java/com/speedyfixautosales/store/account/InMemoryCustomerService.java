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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Accounts in memory, for running without a database (a restart clears them). */
@Service
@Profile("!postgres")
public class InMemoryCustomerService implements CustomerService {

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> searches = new ConcurrentHashMap<>();

    @Override
    public Customer signIn(GoogleTokenVerifier.GoogleIdentity google) {
        Instant now = Instant.now();
        return customers.compute(google.sub(), (id, existing) -> new Customer(id, google.email(), google.name(),
                google.pictureUrl(), existing != null ? existing.joinedAt() : now, now,
                existing != null ? existing.signIns() + 1 : 1));
    }

    @Override
    public List<Customer> all() {
        return customers.values().stream().sorted(Comparator.comparing(Customer::lastSignInAt).reversed()).toList();
    }

    @Override
    public Customer get(String id) {
        Customer c = id == null ? null : customers.get(id);
        if (c == null) {
            throw new NotFoundException("We couldn’t find your account. Sign in again.");
        }
        return c;
    }

    @Override
    public List<String> searches(String customerId) {
        Deque<String> list = searches.get(customerId);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    @Override
    public List<String> recordSearch(String customerId, String query) {
        String q = CustomerService.tidy(query);
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

    @Override
    public void clearSearches(String customerId) {
        searches.remove(customerId);
    }
}
