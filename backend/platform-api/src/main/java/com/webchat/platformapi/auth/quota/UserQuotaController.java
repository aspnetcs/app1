package com.webchat.platformapi.auth.quota;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * User-facing endpoint to query their own role policy, daily quota usage, and credits.
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserQuotaController {

    private final RolePolicyService rolePolicyService;
    private final RolePolicyProperties rolePolicyProperties;
    private final CreditsRuntimeService creditsRuntimeService;

    public UserQuotaController(RolePolicyService rolePolicyService,
                               RolePolicyProperties rolePolicyProperties,
                               @Nullable CreditsRuntimeService creditsRuntimeService) {
        this.rolePolicyService = rolePolicyService;
        this.rolePolicyProperties = rolePolicyProperties;
        this.creditsRuntimeService = creditsRuntimeService;
    }

    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> quota(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        String effectiveRole = role == null || role.isBlank() ? "user" : role.trim().toLowerCase();
        String policyRole = "pending".equals(effectiveRole) ? "user" : effectiveRole;
        long dailyUsed = rolePolicyService.getDailyUsageCount(userId);
        Set<String> allowedModels = rolePolicyService.resolveAllowedModels(userId, effectiveRole);

        Map<String, Object> dailyQuota = new LinkedHashMap<>();
        dailyQuota.put("used", dailyUsed);
        dailyQuota.put("limit", rolePolicyService.resolveDailyQuota(effectiveRole));

        Map<String, Object> rateLimit = new LinkedHashMap<>();
        rateLimit.put("perMinute", rolePolicyService.resolveRateLimit(userId, effectiveRole));
        rateLimit.put("modelPerMinute", rolePolicyService.resolveModelRateLimit(effectiveRole));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", effectiveRole);
        result.put("dailyQuota", dailyQuota);
        result.put("rateLimit", rateLimit);
        result.put("allowedModelCount", allowedModels.isEmpty() ? -1 : allowedModels.size());

        // Credits system info
        if (creditsRuntimeService != null) {
            result.put("credits", creditsRuntimeService.getAccountSummary(userId, null, effectiveRole));
        }

        return ApiResponse.ok(result);
    }
}
