package com.webchat.platformapi.infra.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Restrict API access to local/dev origins instead of exposing broad cross-site access.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public CorsConfig(@Value("${cors.api-allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String patterns) {
        this.allowedOriginPatterns = patterns == null ? new String[0] : patterns.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type", "X-Guest-Recovery-Token")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
