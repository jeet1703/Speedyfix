package com.speedyfixautosales.store.account;

import com.speedyfixautosales.store.admin.AdminAuthInterceptor;
import com.speedyfixautosales.store.common.UnauthorizedException;
import com.speedyfixautosales.store.common.UnavailableException;
import com.speedyfixautosales.store.order.Order;
import com.speedyfixautosales.store.order.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
 * Shopper accounts: sign in with Google or (once it's switched on) an email address and password, see who's signed in,
 * sign out, the account's orders and recent searches. Every route but the sign-in and sign-up ones needs
 * "Authorization: Bearer <account token>".
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final GoogleTokenVerifier google;
    private final CustomerService customers;
    private final CustomerAuthService auth;
    private final OrderService orders;
    private final EmailSignInService emailSignIn;

    public AccountController(GoogleTokenVerifier google, CustomerService customers, CustomerAuthService auth, OrderService orders,
                             EmailSignInService emailSignIn) {
        this.google = google;
        this.customers = customers;
        this.auth = auth;
        this.orders = orders;
        this.emailSignIn = emailSignIn;
    }

    public record GoogleSignIn(@NotBlank(message = "Google didn’t send a sign-in. Try again.") @Size(max = 4096) String credential) {
    }

    public record SignedIn(String token, Instant expiresAt, CustomerService.Customer customer) {
    }

    public record EmailSignUp(
            @NotBlank(message = "Enter your name.") @Size(max = 100, message = "Keep your name under 100 characters.") String name,
            @NotBlank(message = "Enter your email address.") @Email(message = "Enter an email address like name@example.com.")
            @Size(max = 200, message = "Keep your email address under 200 characters.") String email,
            @NotBlank(message = "Create a password.") @Size(max = 200, message = "Keep your password under 200 characters.") String password) {
    }

    public record PasswordReset(
            @NotBlank(message = "Enter your email address.") @Size(max = 200) String email,
            @NotBlank(message = "Enter the 6-digit code from the email.") @Pattern(regexp = "\\s*\\d{6}\\s*",
                    message = "Enter the 6-digit code from the email.") String code,
            @NotBlank(message = "Choose a new password.") @Size(max = 200, message = "Keep your password under 200 characters.") String password) {
    }

    public record EmailCode(
            @NotBlank(message = "Enter your email address.") @Size(max = 200) String email,
            @NotBlank(message = "Enter the 6-digit code from the email.") @Pattern(regexp = "\\s*\\d{6}\\s*",
                    message = "Enter the 6-digit code from the email.") String code) {
    }

    public record EmailOnly(@NotBlank(message = "Enter your email address.") @Size(max = 200) String email) {
    }

    public record EmailSignIn(
            @NotBlank(message = "Enter your email address.") @Size(max = 200) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 200) String password) {
    }

    public record SearchEntry(@NotBlank(message = "Enter a search.") @Size(max = 100, message = "Keep searches under 100 characters.") String query) {
    }

    @PostMapping("/google")
    public SignedIn signInWithGoogle(@Valid @RequestBody GoogleSignIn body) {
        if (!google.enabled()) {
            throw new UnavailableException("Google sign-in isn’t set up yet. Set GOOGLE_CLIENT_ID on the server.");
        }
        return session(customers.signIn(google.verify(body.credential())));
    }

    /** Emails a 6-digit code to confirm the address; the account is made when it comes back to /email/confirm. */
    @PostMapping("/email/sign-up")
    public ResponseEntity<Void> signUpWithEmail(@Valid @RequestBody EmailSignUp body) {
        emailSignIn.startSignUp(body.name(), body.email(), body.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/resend")
    public ResponseEntity<Void> resendCode(@Valid @RequestBody EmailOnly body) {
        emailSignIn.resendCode(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/confirm")
    public SignedIn confirmEmail(@Valid @RequestBody EmailCode body) {
        return session(emailSignIn.confirm(body.email(), body.code()));
    }

    /** "Forgot password": emails a code to set a new one (once a day per account; the same answer for any address). */
    @PostMapping("/email/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailOnly body) {
        emailSignIn.startReset(body.email());
        return ResponseEntity.noContent().build();
    }

    /** Sets the new password with the emailed code, and signs the shopper in. */
    @PostMapping("/email/reset")
    public SignedIn resetPassword(@Valid @RequestBody PasswordReset body) {
        return session(emailSignIn.resetPassword(body.email(), body.code(), body.password()));
    }

    @PostMapping("/email/sign-in")
    public SignedIn signInWithEmail(@Valid @RequestBody EmailSignIn body) {
        return session(emailSignIn.signIn(body.email(), body.password()));
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
     * (Google, or the code emailed at sign-up, has confirmed the shopper owns it).
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

    private SignedIn session(CustomerService.Customer customer) {
        CustomerAuthService.Session session = auth.issue(customer.id());
        return new SignedIn(session.token(), session.expiresAt(), customer);
    }

    private String customerId(HttpServletRequest request) {
        return auth.customerId(AdminAuthInterceptor.bearerToken(request))
                .orElseThrow(() -> new UnauthorizedException("Sign in to see your account."));
    }
}
