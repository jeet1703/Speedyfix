package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.common.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts and their recent searches in PostgreSQL (customers, customer_searches). */
@Service
@Profile("postgres")
public class JdbcCustomerService implements CustomerService {

    private static final String COLUMNS = "id, email, name, picture_url, joined_at, last_sign_in_at, sign_ins";

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
                rs.getTimestamp("joined_at").toInstant(), rs.getTimestamp("last_sign_in_at").toInstant(), rs.getInt("sign_ins"));
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
