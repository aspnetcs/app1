package com.webchat.adminapi;

import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/ai/usage")
public class AdminUsageController {

    private final AiUsageService usageService;

    public AdminUsageController(AiUsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> adminUsage(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        int d = Math.min(Math.max(1, days), 365);
        Instant from = Instant.now().minus(d, ChronoUnit.DAYS);
        return ApiResponse.ok(usageService.getAdminSummary(from, Instant.now()));
    }
}

