package com.webchat.platformapi.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.auth.role.RolePolicyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AiRateLimitConfig {

    @Bean
    public FilterRegistrationBean<AiRateLimitFilter> aiRateLimitFilterRegistration(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            RolePolicyProperties rolePolicyProperties,
            RolePolicyService rolePolicyService,
            @Value("${ai.rate-limit-per-minute:60}") int maxPerMinute,
            @Value("${ai.model-rate-limit-per-minute:0}") int modelMaxPerMinute
    ) {
        FilterRegistrationBean<AiRateLimitFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new AiRateLimitFilter(redis, objectMapper, maxPerMinute, modelMaxPerMinute, rolePolicyProperties, rolePolicyService));
        reg.addUrlPatterns("/api/v1/chat/*", "/v1/*");
        reg.setOrder(2); // after JwtAuthFilter (order=1) so userId attribute is available
        return reg;
    }
}
