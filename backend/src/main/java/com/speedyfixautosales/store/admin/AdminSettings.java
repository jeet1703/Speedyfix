package com.speedyfixautosales.store.admin;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Settings staff change on the admin Settings page. Starting values come from application.yml (prefix "admin");
 * changes are held in memory, so a redeploy puts them back.
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

    private volatile Values values;

    public AdminSettings(@Value("${admin.lookup-cost:0}") BigDecimal lookupCost,
                         @Value("${admin.lookup-budget:0}") BigDecimal lookupBudget,
                         @Value("${admin.buying-team-email:}") String buyingTeamEmail) {
        this.values = new Values(lookupCost, lookupBudget, buyingTeamEmail.trim());
    }

    public Values get() {
        return values;
    }

    public Values set(Values newValues) {
        this.values = newValues;
        return newValues;
    }
}
