package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.common.TooManyRequestsException;
import com.speedyfixautosales.store.common.UnauthorizedException;
import com.speedyfixautosales.store.common.UnavailableException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Staff sign-in for the admin with an email and password. The master account's email comes from ADMIN_EMAIL and its
 * password from ADMIN_PASSWORD (12+ characters, a secret on Render); without a password, sign-in stays switched off.
 * A successful sign-in returns a signed JWT (HS256). The signing key is ADMIN_JWT_SECRET (32+ characters) so tokens
 * survive a redeploy; without it a random key is made at start-up, which signs everyone out on each restart.
 * Signing out puts the token's id on a deny list until the token would have expired anyway.
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    static final int MIN_PASSWORD_LENGTH = 12;
    static final int MIN_SECRET_LENGTH = 32;
    static final Duration SESSION_LENGTH = Duration.ofHours(12);
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    /** After this many wrong tries from one address, sign-in is locked for LOCKOUT from the last one. */
    static final int MAX_FAILURES_PER_CLIENT = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    // Client addresses come from X-Forwarded-For, which a caller can vary, so there's an overall cap too.
    static final int MAX_FAILURES_OVERALL = 50;

    private static final String ISSUER = "speedyfixautosales-admin";
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Role { MASTER }

    /** Who a token belongs to. */
    public record Staff(String email, String name, Role role) {
    }

    public record Session(String token, String email, String name, Role role, Instant expiresAt) {
    }

    public record SignedIn(Staff staff, Instant expiresAt) {
    }

    private record Account(Staff staff, byte[] passwordHash) {
    }

    /** One address's wrong tries since its last lockout (forgotten after FAILURE_WINDOW without one), and its lock. */
    private record Attempts(int failures, Instant lastFailure, Instant lockedUntil) {
    }

    private final Account master;
    private final SecretKey key;
    private final Map<String, Instant> signedOut = new ConcurrentHashMap<>();
    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();
    private final Deque<Instant> allFailures = new ArrayDeque<>();
    private Clock clock = Clock.systemUTC();

    public AdminAuthService(@Value("${admin.email:}") String email,
                            @Value("${admin.password:}") String password,
                            @Value("${admin.name:Staff}") String name,
                            @Value("${admin.jwt-secret:}") String jwtSecret) {
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        if (password.isBlank() || cleanEmail.isEmpty()) {
            this.master = null;
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD is shorter than {} characters, so admin sign-in is switched off.", MIN_PASSWORD_LENGTH);
            this.master = null;
        } else {
            this.master = new Account(new Staff(cleanEmail, name.isBlank() ? "Staff" : name.trim(), Role.MASTER), sha256(password));
        }

        if (jwtSecret.length() >= MIN_SECRET_LENGTH) {
            this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        } else {
            if (!jwtSecret.isEmpty()) {
                log.warn("ADMIN_JWT_SECRET is shorter than {} characters, so a random key is used instead.", MIN_SECRET_LENGTH);
            }
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            this.key = Keys.hmacShaKeyFor(bytes);
        }
    }

    public boolean enabled() {
        return master != null;
    }

    public Session signIn(String email, String password, String client) {
        if (!enabled()) {
            throw new UnavailableException("Admin sign-in isn’t set up yet. Set ADMIN_EMAIL and ADMIN_PASSWORD (12+ characters) on the server.");
        }
        Instant now = clock.instant();
        synchronized (allFailures) {
            prune(allFailures, now);
            Attempts mine = current(client, now);
            if (mine.lockedUntil() != null) {
                throw new TooManyRequestsException("Too many failed sign-ins. Try again in " + minutes(now, mine.lockedUntil()) + ".");
            }
            if (allFailures.size() >= MAX_FAILURES_OVERALL) {
                throw new TooManyRequestsException("Too many failed sign-ins. Wait 15 minutes and try again.");
            }
            // Check the password even when the email is wrong, so neither answer comes back faster.
            boolean emailMatches = email != null && master.staff().email().equals(email.trim().toLowerCase(Locale.ROOT));
            boolean passwordMatches = password != null && MessageDigest.isEqual(master.passwordHash(), sha256(password));
            if (!emailMatches || !passwordMatches) {
                allFailures.addLast(now);
                int count = mine.failures() + 1;
                // Entries are only created here, so the overall cap also bounds the map's size.
                if (count >= MAX_FAILURES_PER_CLIENT) {
                    failures.put(client, new Attempts(0, now, now.plus(LOCKOUT)));
                    throw new TooManyRequestsException("That email or password isn’t right. After "
                            + MAX_FAILURES_PER_CLIENT + " wrong tries, sign-in is locked for 15 minutes.");
                }
                failures.put(client, new Attempts(count, now, null));
                int left = MAX_FAILURES_PER_CLIENT - count;
                throw new UnauthorizedException("That email or password isn’t right. " + left + (left == 1 ? " try" : " tries")
                        + " left before a 15-minute wait.");
            }
            failures.remove(client);
        }
        Staff staff = master.staff();
        Instant expires = now.plus(SESSION_LENGTH);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(staff.email())
                .claim("name", staff.name())
                .claim("role", staff.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new Session(token, staff.email(), staff.name(), staff.role(), expires);
    }

    /** Who a live token belongs to and when it ends; empty if it's missing, forged, expired or signed out. */
    public Optional<SignedIn> check(String token) {
        return claims(token)
                .filter(c -> master != null && master.staff().email().equals(c.getSubject()))
                .map(c -> new SignedIn(master.staff(), c.getExpiration().toInstant()));
    }

    public void signOut(String token) {
        claims(token).ifPresent(c -> signedOut.put(c.getId(), c.getExpiration().toInstant()));
    }

    @Scheduled(fixedDelayString = "PT1H")
    void removeExpired() {
        Instant now = clock.instant();
        signedOut.values().removeIf(e -> e.isBefore(now));
        synchronized (allFailures) {
            failures.keySet().removeIf(client -> current(client, now).failures() == 0 && current(client, now).lockedUntil() == null);
        }
    }

    /** The address's tries as they stand now: a lock that has run out, or tries older than FAILURE_WINDOW, are forgotten. */
    private Attempts current(String client, Instant now) {
        Attempts a = failures.get(client);
        if (a == null || (a.lockedUntil() != null && !now.isBefore(a.lockedUntil()))
                || (a.lockedUntil() == null && a.lastFailure().plus(FAILURE_WINDOW).isBefore(now))) {
            return new Attempts(0, now, null);
        }
        return a;
    }

    /** "12 minutes", rounded up, never "0 minutes". */
    private static String minutes(Instant now, Instant until) {
        long m = Math.max(1, (Duration.between(now, until).getSeconds() + 59) / 60);
        return m + (m == 1 ? " minute" : " minutes");
    }

    /** Tests move time on with a fixed clock. */
    void useClock(Clock clock) {
        this.clock = clock;
    }

    private Optional<Claims> claims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build().parseSignedClaims(token).getPayload();
            return c.getId() == null || signedOut.containsKey(c.getId()) ? Optional.empty() : Optional.of(c);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void prune(Deque<Instant> times, Instant now) {
        while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(FAILURE_WINDOW))) {
            times.removeFirst();
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
