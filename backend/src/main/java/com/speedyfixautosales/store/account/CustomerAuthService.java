package com.speedyfixautosales.store.account;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
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
 * Shopper sessions: a signed JWT (HS256) issued after a Google sign-in, kept apart from staff sessions by its own
 * key and issuer. The key is ACCOUNT_JWT_SECRET (32+ characters) so sessions survive a redeploy; without it a
 * random key is made at start-up. Signing out puts the token's id on a deny list until it would have expired.
 */
@Service
public class CustomerAuthService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAuthService.class);

    static final Duration SESSION_LENGTH = Duration.ofDays(30);
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String ISSUER = "speedyfixautosales-account";

    public record Session(String token, Instant expiresAt) {
    }

    private final SecretKey key;
    private final Map<String, Instant> signedOut = new ConcurrentHashMap<>();

    public CustomerAuthService(@Value("${account.jwt-secret:}") String secret) {
        if (secret.length() >= MIN_SECRET_LENGTH) {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } else {
            if (!secret.isEmpty()) {
                log.warn("ACCOUNT_JWT_SECRET is shorter than {} characters, so a random key is used instead.", MIN_SECRET_LENGTH);
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            this.key = Keys.hmacShaKeyFor(bytes);
        }
    }

    public Session issue(String customerId) {
        Instant now = Instant.now();
        Instant expires = now.plus(SESSION_LENGTH);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(customerId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new Session(token, expires);
    }

    /** The customer id a live token belongs to; empty if it's missing, forged, expired or signed out. */
    public Optional<String> customerId(String token) {
        return claims(token).map(Claims::getSubject);
    }

    public void signOut(String token) {
        claims(token).ifPresent(c -> signedOut.put(c.getId(), c.getExpiration().toInstant()));
    }

    @Scheduled(fixedDelayString = "PT1H")
    void removeExpired() {
        Instant now = Instant.now();
        signedOut.values().removeIf(e -> e.isBefore(now));
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
}
