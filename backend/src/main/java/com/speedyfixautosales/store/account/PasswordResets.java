package com.speedyfixautosales.store.account;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * "Forgot password" codes, one per email account: the password_resets table with the "postgres" profile (so the
 * once-a-day limit survives a restart), memory without it. codeHash is null once the code has been used.
 */
@Component
class PasswordResets {

    record Reset(String emailKey, String codeHash, Instant expiresAt, int attempts, Instant requestedAt) {
    }

    /** Null without the database. */
    private final JdbcTemplate jdbc;
    private final Map<String, Reset> memory = new ConcurrentHashMap<>();

    PasswordResets(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc.getIfAvailable();
    }

    Optional<Reset> find(String emailKey) {
        if (jdbc == null) {
            return Optional.ofNullable(memory.get(emailKey));
        }
        return jdbc.query("SELECT email_key, code_hash, expires_at, attempts, requested_at FROM password_resets WHERE email_key = ?",
                (rs, row) -> new Reset(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getInt(4),
                        rs.getTimestamp(5).toInstant()), emailKey).stream().findFirst();
    }

    void save(Reset r) {
        if (jdbc == null) {
            memory.put(r.emailKey(), r);
            return;
        }
        jdbc.update("""
                INSERT INTO password_resets (email_key, code_hash, expires_at, attempts, requested_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (email_key) DO UPDATE SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at,
                    attempts = EXCLUDED.attempts, requested_at = EXCLUDED.requested_at""",
                r.emailKey(), r.codeHash(), Timestamp.from(r.expiresAt()), r.attempts(), Timestamp.from(r.requestedAt()));
    }
}
