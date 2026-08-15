package com.webchat.platformapi.ai.usage;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.credits.CreditSettlementSnapshotEntity;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class UserUsageController {

    private final AiUsageService usageService;
    private final CreditsRuntimeService creditsRuntimeService;

    public UserUsageController(AiUsageService usageService,
                               @Nullable CreditsRuntimeService creditsRuntimeService) {
        this.usageService = usageService;
        this.creditsRuntimeService = creditsRuntimeService;
    }

    @GetMapping("/ai/usage/me")
    public ApiResponse<Map<String, Object>> myUsage(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        return ApiResponse.ok(usageService.getUserSummary(userId, from, Instant.now()));
    }

    @GetMapping("/ai/usage/credits")
    public ApiResponse<Map<String, Object>> creditsHistory(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        if (creditsRuntimeService == null) {
            Map<String, Object> disabled = new LinkedHashMap<>();
            disabled.put("creditsSystemEnabled", false);
            disabled.put("freeModeEnabled", false);
            disabled.put("account", Map.of(
                    "hasAccount", false,
                    "creditsSystemEnabled", false,
                    "freeModeEnabled", false
            ));
            disabled.put("total", 0);
            disabled.put("page", safePage);
            disabled.put("size", safeSize);
            disabled.put("settlements", List.of());
            return ApiResponse.ok(disabled);
        }

        Map<String, Object> accountSummary = creditsRuntimeService.getAccountSummary(userId, null, role);
        var historyPage = creditsRuntimeService.getHistoryPage(
                userId,
                null,
                org.springframework.data.domain.PageRequest.of(safePage, safeSize)
        );
        List<Map<String, Object>> items = historyPage.getContent().stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("requestId", s.getRequestId());
            item.put("modelId", s.getModelId());
            item.put("modelName", s.getModelNameSnapshot());
            item.put("status", s.getSettlementStatus());
            item.put("reservedCredits", s.getReservedCredits());
            item.put("settledCredits", s.getSettledCredits());
            item.put("refundedCredits", s.getRefundedCredits());
            item.put("inputTokens", s.getInputTokens());
            item.put("outputTokens", s.getOutputTokens());
            item.put("createdAt", s.getCreatedAt());
            item.put("settledAt", s.getSettledAt());
            return item;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("creditsSystemEnabled", accountSummary.getOrDefault("creditsSystemEnabled", false));
        result.put("freeModeEnabled", accountSummary.getOrDefault("freeModeEnabled", false));
        result.put("account", accountSummary);
        result.put("total", historyPage.getTotalElements());
        result.put("page", historyPage.getNumber());
        result.put("size", historyPage.getSize());
        result.put("settlements", items);
        return ApiResponse.ok(result);
    }
}
