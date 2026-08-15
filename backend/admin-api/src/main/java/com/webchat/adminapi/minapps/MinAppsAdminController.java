package com.webchat.adminapi.minapps;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/minapps")
public class MinAppsAdminController {

    private static final String FLAG_NAME = "platform.minapps.enabled";

    private final Environment environment;

    public MinAppsAdminController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        boolean enabled = Boolean.parseBoolean(environment.getProperty(FLAG_NAME, "false"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("flagName", FLAG_NAME);
        payload.put("policy", "read-only");
        payload.put("reason", "MinApps is intentionally disabled. Admin console only provides an explanation page.");
        return ApiResponse.ok(payload);
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        return null;
    }
}

