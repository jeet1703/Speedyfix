package com.speedyfixautosales.store.admin;

import com.speedyfixautosales.store.web.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthService auth;
    private final RateLimitInterceptor rateLimits;

    public AdminWebConfig(AdminAuthService auth, RateLimitInterceptor rateLimits) {
        this.auth = auth;
        this.rateLimits = rateLimits;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimits).addPathPatterns("/api/**");
        registry.addInterceptor(new AdminAuthInterceptor(auth))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/session");
    }
}
