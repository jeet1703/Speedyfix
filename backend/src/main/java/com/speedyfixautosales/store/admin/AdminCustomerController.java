package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.account.CustomerService;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shoppers who have signed in with Google, for staff. Every route needs a staff session (AdminWebConfig). */
@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {

    private final CustomerService customers;

    public AdminCustomerController(CustomerService customers) {
        this.customers = customers;
    }

    public record Row(String id, String name, String email, String pictureUrl, Instant joinedAt, Instant lastSignInAt,
                      int signIns, List<String> recentSearches) {
    }

    /** Most recently signed in first, each with their recent searches. */
    @GetMapping
    public List<Row> all() {
        return customers.all().stream()
                .map(c -> new Row(c.id(), c.name(), c.email(), c.pictureUrl(), c.joinedAt(), c.lastSignInAt(), c.signIns(),
                        customers.searches(c.id())))
                .toList();
    }
}
