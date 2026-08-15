package com.webchat.adminapi.onboarding;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/onboarding")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class OnboardingAdminController {

    private final OnboardingAdminService onboardingAdminService;

    public OnboardingAdminController(OnboardingAdminService onboardingAdminService) {
        this.onboardingAdminService = onboardingAdminService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return ApiResponse.ok(onboardingAdminService.getConfig());
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        try {
            return ApiResponse.ok(onboardingAdminService.updateConfig(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @GetMapping("/state")
    public ApiResponse<Map<String, Object>> getUserState(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(name = "userId") String userIdValue
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        try {
            return ApiResponse.ok(onboardingAdminService.getUserState(UUID.fromString(userIdValue)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "userId is invalid");
        }
    }

    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> resetUserState(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        try {
            String raw = body == null || body.get("userId") == null ? "" : String.valueOf(body.get("userId")).trim();
            if (raw.isEmpty()) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "userId is required");
            }
            return ApiResponse.ok(onboardingAdminService.resetUserState(UUID.fromString(raw)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "userId is invalid");
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