package com.webchat.platformapi.auth.jwt;

import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtFilterConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtService jwtService,
            DeviceSessionRepository deviceSessionRepository
    ) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new JwtAuthFilter(jwtService, deviceSessionRepository));
        reg.addUrlPatterns("/api/*", "/v1/*");
        reg.setOrder(1);
        return reg;
    }
}
