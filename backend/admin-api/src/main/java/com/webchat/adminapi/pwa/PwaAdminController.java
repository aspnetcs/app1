package com.webchat.adminapi.pwa;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/pwa")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class PwaAdminController {

    private final boolean enabled;
    private final boolean forceRefresh;

    public PwaAdminController(
            @org.springframework.beans.factory.annotation.Value("${platform.pwa.enabled:false}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${platform.pwa.force-refresh:false}") boolean forceRefresh
    ) {
        this.enabled = enabled;
        this.forceRefresh = forceRefresh;
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
                "forceRefresh", forceRefresh,
                "manifestPath", "/manifest.webmanifest",
                "serviceWorkerPath", "/sw.js",
                "cacheStrategy", "static-assets"
        ));
    }
}



