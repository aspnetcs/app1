package com.webchat.adminapi.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for admin/dev tooling access.
 * Restrict to local origins plus file:// (Origin: null) instead of allowing every site.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;
    private final String[] adminStaticLocations;

    public CorsConfig(
            @Value("${cors.api-allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,null}") String patterns,
            @Value("${admin.static-locations:file:../../admin/dist/,file:/app/admin-dist/}") String staticLocations) {
        this.allowedOriginPatterns = splitCsv(patterns);
        this.adminStaticLocations = splitCsv(staticLocations);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/admin/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Prefer the local repo build output during development and the packaged
        // runtime copy under /app/admin-dist/ in containerized deployments.
        registry.addResourceHandler("/admin/**")
                .addResourceLocations(adminStaticLocations);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("redirect:/admin/index.html");
        registry.addViewController("/admin/").setViewName("redirect:/admin/index.html");
    }

    private static String[] splitCsv(String raw) {
        return raw == null ? new String[0] : raw.split("\\s*,\\s*");
    }
}


