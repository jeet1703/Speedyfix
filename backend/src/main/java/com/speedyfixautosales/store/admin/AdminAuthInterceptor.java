package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Every /api/admin request except sign-in needs "Authorization: Bearer <JWT>" from a live session.
 * The signed-in staff member is left on the request for controllers (see {@link #staff}).
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String STAFF = AdminAuthInterceptor.class.getName() + ".staff";

    private final AdminAuthService auth;

    public AdminAuthInterceptor(AdminAuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        AdminAuthService.SignedIn signedIn = auth.check(bearerToken(request))
                .orElseThrow(() -> new UnauthorizedException("Your session has ended. Sign in again."));
        request.setAttribute(STAFF, signedIn.staff());
        return true;
    }

    /** The staff member making this request. Only set on routes behind this interceptor. */
    public static AdminAuthService.Staff staff(HttpServletRequest request) {
        return (AdminAuthService.Staff) request.getAttribute(STAFF);
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }
}
