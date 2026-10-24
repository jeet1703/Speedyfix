package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign in, check and sign out. Not behind AdminAuthInterceptor, so each method checks the token itself. */
@RestController
@RequestMapping("/api/admin/session")
public class AdminSessionController {

    private final AdminAuthService auth;

    public AdminSessionController(AdminAuthService auth) {
        this.auth = auth;
    }

    public record SignInRequest(
            @NotBlank(message = "Enter your email address.") @Size(max = 200) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 200) String password) {
    }

    public record SessionInfo(String email, String name, AdminAuthService.Role role, Instant expiresAt) {
    }

    @PostMapping
    public AdminAuthService.Session signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest request) {
        return auth.signIn(body.email(), body.password(), request.getRemoteAddr());
    }

    @GetMapping
    public SessionInfo current(HttpServletRequest request) {
        AdminAuthService.SignedIn signedIn = auth.check(AdminAuthInterceptor.bearerToken(request))
                .orElseThrow(() -> new UnauthorizedException("Your session has ended. Sign in again."));
        AdminAuthService.Staff staff = signedIn.staff();
        return new SessionInfo(staff.email(), staff.name(), staff.role(), signedIn.expiresAt());
    }

    @DeleteMapping
    public ResponseEntity<Void> signOut(HttpServletRequest request) {
        auth.signOut(AdminAuthInterceptor.bearerToken(request));
        return ResponseEntity.noContent().build();
    }
}
