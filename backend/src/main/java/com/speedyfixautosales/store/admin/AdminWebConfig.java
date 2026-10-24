package com.speedyfixautosales.store.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthService auth;

    public AdminWebConfig(AdminAuthService auth) {
        this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(auth))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/session");
    }
}
