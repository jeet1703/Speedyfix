package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.common.BadRequestException;
import com.speedyfixautosales.store.common.TooManyRequestsException;
import com.speedyfixautosales.store.common.UnauthorizedException;
import com.speedyfixautosales.store.common.UnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Accounts made with an email address and password, alongside Google sign-in. Signing up emails a 6-digit code; the
 * account is only created once that code comes back, so every email account's address is confirmed (which is what
 * lets My orders show guest orders placed with it). Waiting sign-ups live in memory for 15 minutes: a restart just
 * means asking for a new code.
 *
 * <p>Switched off unless ACCOUNT_EMAIL_SIGN_IN is true, and that needs a VerificationMailer that really sends email
 * (until then codes only go to the server log).
 */
@Service
public class EmailSignInService {

    private static final Logger log = LoggerFactory.getLogger(EmailSignInService.class);

    static final int MIN_PASSWORD_LENGTH = 8;
    static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    static final int MAX_CODE_ATTEMPTS = 5;
    static final int MAX_CODES_SENT = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final int MAX_FAILURES_PER_EMAIL = 5;
    /** One "Forgot password" code per account in this time. */
    static final Duration RESET_EVERY = Duration.ofDays(1);
    private static final int MAX_PENDING = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Checked against when there's no such account, so a wrong email takes as long to answer as a wrong password. */
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password");

    /** A sign-up waiting for its code. */
    private record Pending(String email, String name, String passwordHash, byte[] codeHash, Instant expiresAt,
                           int attempts, int codesSent) {
    }

    private final boolean enabled;
    private final CustomerService customers;
    private final VerificationMailer mailer;
    private final PasswordResets resets;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public EmailSignInService(@Value("${account.email-sign-in:false}") boolean enabled, CustomerService customers,
                              VerificationMailer mailer, PasswordResets resets) {
        this.enabled = enabled;
        this.customers = customers;
        this.mailer = mailer;
        this.resets = resets;
        if (enabled && !mailer.sendsEmail()) {
            log.warn("Email sign-in is on but email isn’t set up: sign-up codes only go to this log. Don’t use this live.");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Starts a sign-up: emails a code, or, when the address already has an account, an email saying so. The answer is
     * the same either way, so the form doesn't reveal who has an account.
     */
    public void startSignUp(String name, String email, String password) {
        requireEnabled();
        String address = email.trim();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Use at least " + MIN_PASSWORD_LENGTH + " characters for your password.");
        }
        var existing = customers.accountFor(address);
        if (existing.isPresent()) {
            mailer.sendAlreadyRegistered(address, existing.get());
            return;
        }
        if (pending.size() >= MAX_PENDING) {
            throw new TooManyRequestsException("Too many sign-ups right now. Wait a few minutes and try again.");
        }
        String code = newCode();
        pending.put(key(address), new Pending(address, name.trim(), PasswordHasher.hash(password), sha256(code),
                Instant.now().plus(CODE_LIFETIME), 0, 1));
        mailer.sendCode(address, name.trim(), code);
    }

    /** Sends a fresh code for a sign-up still waiting; quietly does nothing otherwise. */
    public void resendCode(String email) {
        requireEnabled();
        String code = newCode();
        Pending p = pending.computeIfPresent(key(email), (k, old) -> {
            if (old.codesSent() >= MAX_CODES_SENT) {
                throw new TooManyRequestsException("We’ve sent several codes already. Wait a few minutes, then sign up again.");
            }
            return new Pending(old.email(), old.name(), old.passwordHash(), sha256(code), Instant.now().plus(CODE_LIFETIME),
                    0, old.codesSent() + 1);
        });
        if (p != null) {
            mailer.sendCode(p.email(), p.name(), code);
        }
    }

    /** Checks the emailed code and, when it's right, creates the account. */
    public CustomerService.Customer confirm(String email, String code) {
        requireEnabled();
        String k = key(email);
        Pending p = pending.get(k);
        if (p == null || p.expiresAt().isBefore(Instant.now())) {
            pending.remove(k);
            throw new BadRequestException("That code has expired. Sign up again to get a new one.");
        }
        if (!MessageDigest.isEqual(p.codeHash(), sha256(code.trim()))) {
            if (p.attempts() + 1 >= MAX_CODE_ATTEMPTS) {
                pending.remove(k);
                throw new BadRequestException("That code isn’t right, and that was the last try. Sign up again to get a new code.");
            }
            pending.replace(k, p, new Pending(p.email(), p.name(), p.passwordHash(), p.codeHash(), p.expiresAt(),
                    p.attempts() + 1, p.codesSent()));
            throw new BadRequestException("That code isn’t right. Check the latest email we sent and try again.");
        }
        if (!pending.remove(k, p)) {
            throw new BadRequestException("That code has already been used. Sign in instead.");
        }
        return customers.createWithPassword(p.email(), p.name(), p.passwordHash());
    }

    public CustomerService.Customer signIn(String email, String password) {
        requireEnabled();
        String k = key(email);
        Instant now = Instant.now();
        Deque<Instant> mine = failures.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (mine) {
            prune(mine, now);
            if (mine.size() >= MAX_FAILURES_PER_EMAIL) {
                throw new TooManyRequestsException("Too many wrong passwords. Wait 15 minutes and try again.");
            }
            var account = customers.passwordAccount(email);
            boolean ok = PasswordHasher.matches(password, account.map(CustomerService.PasswordAccount::passwordHash).orElse(DUMMY_HASH))
                    && account.isPresent();
            if (!ok) {
                mine.addLast(now);
                throw new UnauthorizedException("That email or password isn’t right.");
            }
            failures.remove(k);
            String id = account.get().customer().id();
            // The only time the password is at hand: bring an older, weaker hash up to the current strength.
            if (PasswordHasher.needsRehash(account.get().passwordHash())) {
                customers.setPassword(id, PasswordHasher.hash(password));
            }
            return customers.recordSignIn(id);
        }
    }

    /**
     * "Forgot password": emails a code to set a new password, at most once a day per account. The answer is the same
     * whether or not the address has an email account, or has already had its code today, so the form doesn't reveal
     * who has an account.
     */
    public void startReset(String email) {
        requireEnabled();
        String k = key(email);
        var account = customers.passwordAccount(email);
        if (account.isEmpty()) {
            return;
        }
        synchronized (resets) {
            Instant now = Instant.now();
            var last = resets.find(k);
            if (last.isPresent() && last.get().requestedAt().isAfter(now.minus(RESET_EVERY))) {
                log.info("Password reset for {} not sent: one was already sent in the last day", k);
                return;
            }
            String code = newCode();
            resets.save(new PasswordResets.Reset(k, hex(sha256(code)), now.plus(CODE_LIFETIME), 0, now));
            mailer.sendResetCode(account.get().customer().email(), account.get().customer().name(), code);
        }
    }

    /** Checks the emailed reset code and, when it's right, sets the new password and signs the shopper in. */
    public CustomerService.Customer resetPassword(String email, String code, String newPassword) {
        requireEnabled();
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Use at least " + MIN_PASSWORD_LENGTH + " characters for your password.");
        }
        String k = key(email);
        synchronized (resets) {
            var reset = resets.find(k).filter(r -> r.codeHash() != null && r.expiresAt().isAfter(Instant.now()));
            var account = customers.passwordAccount(email);
            if (reset.isEmpty() || account.isEmpty()) {
                throw new BadRequestException("That code has expired or has already been used. You can ask for one reset code a day.");
            }
            PasswordResets.Reset r = reset.get();
            if (!MessageDigest.isEqual(r.codeHash().getBytes(StandardCharsets.US_ASCII),
                    hex(sha256(code.trim())).getBytes(StandardCharsets.US_ASCII))) {
                boolean last = r.attempts() + 1 >= MAX_CODE_ATTEMPTS;
                resets.save(new PasswordResets.Reset(k, last ? null : r.codeHash(), r.expiresAt(), r.attempts() + 1, r.requestedAt()));
                throw new BadRequestException(last
                        ? "That code isn’t right, and that was the last try. You can ask for a new code tomorrow, or call us."
                        : "That code isn’t right. Check the email we sent and try again.");
            }
            // Used: the code can't work again, but requestedAt stays for the once-a-day limit.
            resets.save(new PasswordResets.Reset(k, null, r.expiresAt(), r.attempts(), r.requestedAt()));
            customers.setPassword(account.get().customer().id(), PasswordHasher.hash(newPassword));
            failures.remove(k);
            return customers.recordSignIn(account.get().customer().id());
        }
    }

    @Scheduled(fixedDelayString = "PT10M")
    void removeExpired() {
        Instant now = Instant.now();
        pending.values().removeIf(p -> p.expiresAt().isBefore(now));
        failures.values().removeIf(d -> {
            synchronized (d) {
                prune(d, now);
                return d.isEmpty();
            }
        });
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new UnavailableException("Signing in with an email address isn’t switched on yet. Use Google, or shop as a guest.");
        }
    }

    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static void prune(Deque<Instant> times, Instant now) {
        while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(FAILURE_WINDOW))) {
            times.removeFirst();
        }
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
