package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.common.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies Google ID tokens against Google's published signing keys (JWKS), which are fetched on demand and
 * cached for an hour. A token must be signed by Google, issued for this site's client id (GOOGLE_CLIENT_ID),
 * unexpired, and for a verified email address.
 */
@Component
public class GoogleJwksTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleJwksTokenVerifier.class);
    private static final URI CERTS = URI.create("https://www.googleapis.com/oauth2/v3/certs");
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final Duration KEY_CACHE = Duration.ofHours(1);

    private final String clientId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Map<String, Key> keys = Map.of();
    private volatile Instant keysFetchedAt = Instant.EPOCH;

    public GoogleJwksTokenVerifier(@Value("${account.google-client-id:}") String clientId) {
        this.clientId = clientId.trim();
    }

    @Override
    public boolean enabled() {
        return !clientId.isEmpty();
    }

    @Override
    public GoogleIdentity verify(String credential) {
        if (!enabled()) {
            throw new UnauthorizedException("Google sign-in isn’t set up yet.");
        }
        Claims c;
        try {
            c = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            return key(header.getKeyId());
                        }
                    })
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(credential)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("We couldn’t sign you in with Google. Try again.");
        }
        if (!ISSUERS.contains(c.getIssuer()) || !Boolean.TRUE.equals(c.get("email_verified", Boolean.class))) {
            throw new UnauthorizedException("We couldn’t sign you in with Google. Try again.");
        }
        String email = c.get("email", String.class);
        String name = c.get("name", String.class);
        return new GoogleIdentity(c.getSubject(), email, name == null || name.isBlank() ? email : name, c.get("picture", String.class));
    }

    /** The key Google signed with. Unknown key ids trigger one refetch, since Google rotates its keys. */
    private Key key(String kid) {
        Key key = kid == null ? null : keys.get(kid);
        if (key == null || keysFetchedAt.isBefore(Instant.now().minus(KEY_CACHE))) {
            refreshKeys();
            key = kid == null ? null : keys.get(kid);
        }
        if (key == null) {
            throw new UnauthorizedException("We couldn’t sign you in with Google. Try again.");
        }
        return key;
    }

    private synchronized void refreshKeys() {
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(CERTS).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Google signing keys returned HTTP {}", res.statusCode());
                return;
            }
            JwkSet set = Jwks.setParser().build().parse(res.body());
            keys = set.getKeys().stream()
                    .filter(j -> j.getId() != null)
                    .collect(Collectors.toUnmodifiableMap(Jwk::getId, j -> (Key) j.toKey(), (a, b) -> a));
            keysFetchedAt = Instant.now();
        } catch (IOException | JwtException e) {
            log.warn("Couldn’t fetch Google signing keys: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
