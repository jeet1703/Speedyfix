package com.speedyfixautosales.store.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void storesASaltedHashNeverThePassword() {
        String a = PasswordHasher.hash("correct horse battery");
        String b = PasswordHasher.hash("correct horse battery");
        assertThat(a).startsWith("pbkdf2$" + PasswordHasher.ITERATIONS + "$").doesNotContain("correct horse battery");
        assertThat(a).isNotEqualTo(b);
        assertThat(PasswordHasher.matches("correct horse battery", a)).isTrue();
        assertThat(PasswordHasher.matches("correct horse batterY", a)).isFalse();
        assertThat(PasswordHasher.matches("correct horse battery", "correct horse battery")).isFalse();
        assertThat(PasswordHasher.needsRehash(a)).isFalse();
    }

    @Test
    void olderWeakerHashesStillWorkAndAreMarkedForUpgrade() {
        // Made with the earlier 210,000 iterations: salt "c2FsdHNhbHRzYWx0c2FsdA" (16 bytes of "saltsaltsaltsalt").
        String old = "pbkdf2$210000$c2FsdHNhbHRzYWx0c2FsdA$" + oldHash();
        assertThat(PasswordHasher.matches("hunter2hunter2", old)).isTrue();
        assertThat(PasswordHasher.needsRehash(old)).isTrue();
        assertThat(PasswordHasher.needsRehash("not a hash")).isTrue();
    }

    private static String oldHash() {
        try {
            var spec = new javax.crypto.spec.PBEKeySpec("hunter2hunter2".toCharArray(), "saltsaltsaltsalt".getBytes(), 210_000, 256);
            byte[] key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return java.util.Base64.getEncoder().withoutPadding().encodeToString(key);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
