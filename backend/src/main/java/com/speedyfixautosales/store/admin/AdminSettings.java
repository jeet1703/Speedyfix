package com.speedyfixautosales.store.admin;

import java.math.BigDecimal;
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
     * @param lookupCost     what the vehicle data provider charges per plate lookup, GBP
     * @param lookupBudget   monthly cap on lookup spend, GBP; zero means no cap is set
     * @param buyingTeamEmail where "Send list to buying team" addresses its email
     */
    public record Values(BigDecimal lookupCost, BigDecimal lookupBudget, String buyingTeamEmail) {
    }

    /** Null without the database. */
    private final JdbcTemplate jdbc;
    private final Values defaults;
    private volatile Values values;

    public AdminSettings(@Value("${admin.lookup-cost:0}") BigDecimal lookupCost,
                         @Value("${admin.lookup-budget:0}") BigDecimal lookupBudget,
                         @Value("${admin.buying-team-email:}") String buyingTeamEmail,
                         ObjectProvider<JdbcTemplate> jdbc) {
        this.defaults = new Values(lookupCost, lookupBudget, buyingTeamEmail.trim());
        this.jdbc = jdbc.getIfAvailable();
    }

    public Values get() {
        Values v = values;
        if (v == null) {
            v = jdbc == null ? defaults : jdbc.query(
                    "SELECT lookup_cost, lookup_budget, buying_team_email FROM settings WHERE id = 1 AND saved",
                    (rs, row) -> new Values(rs.getBigDecimal("lookup_cost"), rs.getBigDecimal("lookup_budget"),
                            rs.getString("buying_team_email"))).stream().findFirst().orElse(defaults);
            values = v;
        }
        return v;
    }

    public Values set(Values newValues) {
        if (jdbc != null) {
            jdbc.update("""
                    INSERT INTO settings (id, lookup_cost, lookup_budget, buying_team_email, saved) VALUES (1, ?, ?, ?, true)
                    ON CONFLICT (id) DO UPDATE SET lookup_cost = EXCLUDED.lookup_cost, lookup_budget = EXCLUDED.lookup_budget,
                        buying_team_email = EXCLUDED.buying_team_email, saved = true""",
                    newValues.lookupCost(), newValues.lookupBudget(), newValues.buyingTeamEmail());
        }
        this.values = newValues;
        return newValues;
    }
}
