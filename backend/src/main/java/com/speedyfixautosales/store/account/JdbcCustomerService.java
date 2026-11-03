package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.common.ConflictException;
import com.speedyfixautosales.store.common.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts and their recent searches in PostgreSQL (customers, customer_searches). Email accounts are the rows with a
 * password_hash; at most one per address (ignoring case).
 */
@Service
@Profile("postgres")
public class JdbcCustomerService implements CustomerService {

    private static final String COLUMNS =
            "id, email, name, picture_url, joined_at, last_sign_in_at, sign_ins, (password_hash IS NOT NULL) AS email_account";

    private final JdbcTemplate jdbc;

    public JdbcCustomerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Customer signIn(GoogleTokenVerifier.GoogleIdentity google) {
        return jdbc.queryForObject("""
                INSERT INTO customers (id, email, name, picture_url) VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, name = EXCLUDED.name, picture_url = EXCLUDED.picture_url,
                    last_sign_in_at = now(), sign_ins = customers.sign_ins + 1
                RETURNING """ + " " + COLUMNS, JdbcCustomerService::customer,
                google.sub(), cut(google.email(), 200), cut(google.name(), 200), cut(google.pictureUrl(), 500));
    }

    @Override
    public Optional<SignInMethod> accountFor(String email) {
        // An email account wins when Google accounts share the address: that's the one with a password to sign in with.
        return jdbc.query("SELECT " + COLUMNS + " FROM customers WHERE lower(email) = lower(?) ORDER BY password_hash IS NULL LIMIT 1",
                JdbcCustomerService::customer, email.trim()).stream().findFirst().map(Customer::signInMethod);
    }

    @Override
    public Customer createWithPassword(String email, String name, String passwordHash) {
        try {
            return jdbc.queryForObject("INSERT INTO customers (id, email, name, password_hash) VALUES (?, ?, ?, ?) RETURNING " + COLUMNS,
                    JdbcCustomerService::customer, EMAIL_ID_PREFIX + UUID.randomUUID(), cut(email, 200), cut(name, 200), passwordHash);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("This email address already has an account. Sign in instead.");
        }
    }

    @Override
    public Optional<PasswordAccount> passwordAccount(String email) {
        return jdbc.query("SELECT " + COLUMNS + ", password_hash FROM customers WHERE lower(email) = lower(?) AND password_hash IS NOT NULL",
                (rs, row) -> new PasswordAccount(customer(rs, row), rs.getString("password_hash")), email.trim()).stream().findFirst();
    }

    @Override
    public void setPassword(String customerId, String passwordHash) {
        jdbc.update("UPDATE customers SET password_hash = ? WHERE id = ? AND password_hash IS NOT NULL", passwordHash, customerId);
    }

    @Override
    public Customer recordSignIn(String id) {
        return jdbc.query("UPDATE customers SET last_sign_in_at = now(), sign_ins = sign_ins + 1 WHERE id = ? RETURNING " + COLUMNS,
                        JdbcCustomerService::customer, id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("We couldn’t find your account. Sign in again."));
    }

    @Override
    public List<Customer> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM customers ORDER BY last_sign_in_at DESC", JdbcCustomerService::customer);
    }

    @Override
    public Customer get(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM customers WHERE id = ?", JdbcCustomerService::customer, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("We couldn’t find your account. Sign in again."));
    }

    @Override
    public List<String> searches(String customerId) {
        return jdbc.queryForList("SELECT query FROM customer_searches WHERE customer_id = ? ORDER BY searched_at DESC LIMIT ?",
                String.class, customerId, MAX_SEARCHES);
    }

    @Override
    @Transactional
    public List<String> recordSearch(String customerId, String query) {
        String q = CustomerService.tidy(query);
        jdbc.update("DELETE FROM customer_searches WHERE customer_id = ? AND lower(query) = lower(?)", customerId, q);
        jdbc.update("INSERT INTO customer_searches (customer_id, query) VALUES (?, ?)", customerId, q);
        jdbc.update("""
                DELETE FROM customer_searches WHERE customer_id = ? AND query NOT IN (
                    SELECT query FROM customer_searches WHERE customer_id = ? ORDER BY searched_at DESC LIMIT ?)""",
                customerId, customerId, MAX_SEARCHES);
        return searches(customerId);
    }

    @Override
    public void clearSearches(String customerId) {
        jdbc.update("DELETE FROM customer_searches WHERE customer_id = ?", customerId);
    }

    private static Customer customer(ResultSet rs, int row) throws SQLException {
        return new Customer(rs.getString("id"), rs.getString("email"), rs.getString("name"), rs.getString("picture_url"),
                rs.getTimestamp("joined_at").toInstant(), rs.getTimestamp("last_sign_in_at").toInstant(), rs.getInt("sign_ins"),
                rs.getBoolean("email_account") ? SignInMethod.EMAIL : SignInMethod.GOOGLE);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
