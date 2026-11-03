package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.NotFoundException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Accounts in memory, for running without a database (a restart clears them). */
@Service
@Profile("!postgres")
public class InMemoryCustomerService implements CustomerService {

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> searches = new ConcurrentHashMap<>();
    /** Email accounts' password hashes, by customer id. */
    private final Map<String, String> passwords = new ConcurrentHashMap<>();

    @Override
    public Customer signIn(GoogleTokenVerifier.GoogleIdentity google) {
        Instant now = Instant.now();
        return customers.compute(google.sub(), (id, existing) -> new Customer(id, google.email(), google.name(),
                google.pictureUrl(), existing != null ? existing.joinedAt() : now, now,
                existing != null ? existing.signIns() + 1 : 1, SignInMethod.GOOGLE));
    }

    @Override
    public Optional<SignInMethod> accountFor(String email) {
        return byEmail(email, SignInMethod.EMAIL).or(() -> byEmail(email, null)).map(Customer::signInMethod);
    }

    @Override
    public synchronized Customer createWithPassword(String email, String name, String passwordHash) {
        if (byEmail(email, SignInMethod.EMAIL).isPresent()) {
            throw new ConflictException("This email address already has an account. Sign in instead.");
        }
        Instant now = Instant.now();
        Customer c = new Customer(EMAIL_ID_PREFIX + UUID.randomUUID(), email, name, null, now, now, 1, SignInMethod.EMAIL);
        customers.put(c.id(), c);
        passwords.put(c.id(), passwordHash);
        return c;
    }

    @Override
    public Optional<PasswordAccount> passwordAccount(String email) {
        return byEmail(email, SignInMethod.EMAIL).map(c -> new PasswordAccount(c, passwords.get(c.id())));
    }

    @Override
    public void setPassword(String customerId, String passwordHash) {
        passwords.computeIfPresent(customerId, (k, old) -> passwordHash);
    }

    @Override
    public Customer recordSignIn(String id) {
        get(id);
        return customers.computeIfPresent(id, (k, c) -> new Customer(c.id(), c.email(), c.name(), c.pictureUrl(), c.joinedAt(),
                Instant.now(), c.signIns() + 1, c.signInMethod()));
    }

    /** The account using this address (ignoring case), of one sign-in method or (null) any. */
    private Optional<Customer> byEmail(String email, SignInMethod method) {
        return customers.values().stream()
                .filter(c -> c.email().equalsIgnoreCase(email.trim()) && (method == null || c.signInMethod() == method))
                .findFirst();
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
