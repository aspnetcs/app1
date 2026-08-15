package com.webchat.adminapi.credits;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/credits")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class CreditsAdminController {

    private final CreditsAdminService creditsAdminService;

    public CreditsAdminController(CreditsAdminService creditsAdminService) {
        this.creditsAdminService = creditsAdminService;
    }

    public record SystemConfigUpdateRequest(Boolean creditsSystemEnabled, Integer creditsPerUsd, Boolean freeModeEnabled) {}

    public record RoleTemplateUpdateRequest(Long periodCredits, String periodType, Boolean unlimited) {}

    public record AccountAdjustRequest(Long deltaCredits, String reason) {}

    public record AccountResetRequest(String role) {}

    public record AccountGrantSyncRequest(String role, Boolean forceResetPeriod) {}

    @GetMapping("/config")
    public ApiResponse<?> getSystemConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.getSystemConfig();
    }

    @PutMapping("/config")
    public ApiResponse<?> updateSystemConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody(required = false) SystemConfigUpdateRequest request
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.updateSystemConfig(
                request == null ? null : request.creditsSystemEnabled(),
                request == null ? null : request.creditsPerUsd(),
                request == null ? null : request.freeModeEnabled()
        );
    }

    @GetMapping("/role-templates")
    public ApiResponse<?> listRoleTemplates(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.listRoleTemplates();
    }

    @PutMapping("/role-templates/{roleKey}")
    public ApiResponse<?> updateRoleTemplate(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("roleKey") String roleKey,
            @RequestBody(required = false) RoleTemplateUpdateRequest request
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.updateRoleTemplate(
                roleKey,
                request == null ? null : request.periodCredits(),
                request == null ? null : request.periodType(),
                request == null ? null : request.unlimited()
        );
    }

    @GetMapping("/accounts/{userId}")
    public ApiResponse<?> getUserAccountDetail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID adminUserId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("userId") UUID userId
    ) {
        ApiResponse<?> authError = requireAdmin(adminUserId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.getUserAccountDetail(userId);
    }

    @PostMapping("/accounts/{userId}/adjust")
    public ApiResponse<?> adjustUserAccount(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID adminUserId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("userId") UUID userId,
            @RequestBody(required = false) AccountAdjustRequest request
    ) {
        ApiResponse<?> authError = requireAdmin(adminUserId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.adjustUserAccount(
                userId,
                request == null ? null : request.deltaCredits(),
                request == null ? null : request.reason()
        );
    }

    @PostMapping("/accounts/{userId}/reset")
    public ApiResponse<?> resetUserAccount(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID adminUserId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("userId") UUID userId,
            @RequestBody(required = false) AccountResetRequest request
    ) {
        ApiResponse<?> authError = requireAdmin(adminUserId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.resetUserAccount(
                userId,
                request == null ? null : request.role()
        );
    }

    @PostMapping("/accounts/{userId}/grant-sync")
    public ApiResponse<?> grantOrSyncUserAccount(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID adminUserId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("userId") UUID userId,
            @RequestBody(required = false) AccountGrantSyncRequest request
    ) {
        ApiResponse<?> authError = requireAdmin(adminUserId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.grantOrSyncUserAccount(
                userId,
                request == null ? null : request.role(),
                request != null && Boolean.TRUE.equals(request.forceResetPeriod())
        );
    }

    @GetMapping("/settlements")
    public ApiResponse<?> listSettlements(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID adminUserId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(required = false) String modelId
    ) {
        ApiResponse<?> authError = requireAdmin(adminUserId, role);
        if (authError != null) {
            return authError;
        }
        return creditsAdminService.listSettlements(page, size, userId, guestId, requestId, settlementStatus, modelId);
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }
        return null;
    }
}
