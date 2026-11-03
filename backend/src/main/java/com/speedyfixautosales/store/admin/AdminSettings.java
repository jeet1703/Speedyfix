package com.speedyfixautosales.store.admin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Settings staff change on the admin Settings page. Starting values come from application.yml (prefix "admin");
 * once staff save, the values are kept in the settings table with the "postgres" profile (in memory without it).
 */
@Component
public class AdminSettings {

    /**
     * @param buyingTeamEmail where "Send list to buying team" addresses its email
     */
    public record Values(String buyingTeamEmail) {
    }

    /** Null without the database. */
    private final JdbcTemplate jdbc;
    private final Values defaults;
    private volatile Values values;

    public AdminSettings(@Value("${admin.buying-team-email:}") String buyingTeamEmail, ObjectProvider<JdbcTemplate> jdbc) {
        this.defaults = new Values(buyingTeamEmail.trim());
        this.jdbc = jdbc.getIfAvailable();
    }

    public Values get() {
        Values v = values;
        if (v == null) {
            v = jdbc == null ? defaults : jdbc.query(
                    "SELECT buying_team_email FROM settings WHERE id = 1 AND saved",
                    (rs, row) -> new Values(rs.getString("buying_team_email"))).stream().findFirst().orElse(defaults);
            values = v;
        }
        return v;
    }

    public Values set(Values newValues) {
        if (jdbc != null) {
            jdbc.update("""
                    INSERT INTO settings (id, buying_team_email, saved) VALUES (1, ?, true)
                    ON CONFLICT (id) DO UPDATE SET buying_team_email = EXCLUDED.buying_team_email, saved = true""",
                    newValues.buyingTeamEmail());
        }
        this.values = newValues;
        return newValues;
    }
}
