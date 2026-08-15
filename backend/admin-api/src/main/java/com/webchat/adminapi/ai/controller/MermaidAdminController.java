package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/mermaid")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class MermaidAdminController {

    private final boolean enabled;

    public MermaidAdminController(@Value("${platform.mermaid.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        return ApiResponse.ok(Map.of(
                "enabled", enabled,
                "featureKey", "platform.mermaid.enabled",
                "renderMode", "h5-render, non-h5 fallback-to-code"
        ));
    }
}



