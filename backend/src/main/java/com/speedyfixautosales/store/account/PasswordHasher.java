package com.speedyfixautosales.store.account;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Shopper passwords, stored as "pbkdf2$<iterations>$<salt>$<hash>" (PBKDF2 with HMAC-SHA256, a random 16-byte salt
 * each). Built into Java, so no extra library. The iteration count is kept in the hash, so it can be raised later
 * without breaking passwords already saved; older hashes are upgraded when their owner next signs in (needsRehash).
 */
final class PasswordHasher {

    /** OWASP's recommendation for PBKDF2-HMAC-SHA256 (2023). Hashes made before were 210,000. */
    static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODE = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODE = Base64.getDecoder();

    private PasswordHasher() {
    }

    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + ENCODE.encodeToString(salt) + "$" + ENCODE.encodeToString(derive(password, salt, ITERATIONS));
    }

    /** False for a wrong password and for a stored value that isn't one of these hashes. */
    static boolean matches(String password, String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\$");
        if (password == null || parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            byte[] expected = DECODE.decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, DECODE.decode(parts[2]), Integer.parseInt(parts[1])));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** True for a stored hash made with fewer iterations than new ones get, so it should be made again. */
    static boolean needsRehash(String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\$");
        try {
            return parts.length != 4 || Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }
}
