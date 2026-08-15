package com.webchat.adminapi.feature;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/features")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true", matchIfMissing = true)
public class FeatureConfigAdminController {

    private final FeatureConfigAdminService featureConfigAdminService;

    public FeatureConfigAdminController(FeatureConfigAdminService featureConfigAdminService) {
        this.featureConfigAdminService = featureConfigAdminService;
    }

    @GetMapping("/{featureKey}/config")
    public ApiResponse<Map<String, Object>> getConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable String featureKey
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        try {
            return ApiResponse.ok(featureConfigAdminService.getConfig(featureKey));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PutMapping("/{featureKey}/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable String featureKey,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        try {
            return featureConfigAdminService.updateConfig(featureKey, body);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    private static ApiResponse<Map<String, Object>> requireAdmin(UUID userId, String role) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }
        return null;
    }
}
