package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.admin.AdminAuthInterceptor;
import com.speedyfixautosales.store.common.UnauthorizedException;
import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shopper accounts: sign in with Google, see who's signed in, sign out, the account's orders and recent searches.
 * Every route but sign-in needs "Authorization: Bearer <account token>".
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final GoogleTokenVerifier google;
    private final CustomerService customers;
    private final CustomerAuthService auth;
    private final OrderService orders;

    public AccountController(GoogleTokenVerifier google, CustomerService customers, CustomerAuthService auth, OrderService orders) {
        this.google = google;
        this.customers = customers;
        this.auth = auth;
        this.orders = orders;
    }

    public record GoogleSignIn(@NotBlank(message = "Google didn’t send a sign-in. Try again.") @Size(max = 4096) String credential) {
    }

    public record SignedIn(String token, Instant expiresAt, CustomerService.Customer customer) {
    }

    public record SearchEntry(@NotBlank(message = "Enter a search.") @Size(max = 100, message = "Keep searches under 100 characters.") String query) {
    }

    @PostMapping("/google")
    public SignedIn signInWithGoogle(@Valid @RequestBody GoogleSignIn body) {
        if (!google.enabled()) {
            throw new UnavailableException("Google sign-in isn’t set up yet. Set GOOGLE_CLIENT_ID on the server.");
        }
        CustomerService.Customer customer = customers.signIn(google.verify(body.credential()));
        CustomerAuthService.Session session = auth.issue(customer.id());
        return new SignedIn(session.token(), session.expiresAt(), customer);
    }

    @GetMapping
    public CustomerService.Customer me(HttpServletRequest request) {
        return customers.get(customerId(request));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> signOut(HttpServletRequest request) {
        auth.signOut(AdminAuthInterceptor.bearerToken(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * Newest first: orders placed while signed in, plus guest orders placed with the account's email address
     * (Google has confirmed the shopper owns it).
     */
    @GetMapping("/orders")
    public List<Order> orders(HttpServletRequest request) {
        CustomerService.Customer customer = customers.get(customerId(request));
        return orders.forCustomer(customer.id(), customer.email());
    }

    /** Most recent first, up to 10. */
    @GetMapping("/searches")
    public List<String> searches(HttpServletRequest request) {
        return customers.searches(customerId(request));
    }

    @PostMapping("/searches")
    public List<String> recordSearch(@Valid @RequestBody SearchEntry entry, HttpServletRequest request) {
        return customers.recordSearch(customerId(request), entry.query());
    }

    @DeleteMapping("/searches")
    public ResponseEntity<Void> clearSearches(HttpServletRequest request) {
        customers.clearSearches(customerId(request));
        return ResponseEntity.noContent().build();
    }

    private String customerId(HttpServletRequest request) {
        return auth.customerId(AdminAuthInterceptor.bearerToken(request))
                .orElseThrow(() -> new UnauthorizedException("Sign in to see your account."));
    }
}
