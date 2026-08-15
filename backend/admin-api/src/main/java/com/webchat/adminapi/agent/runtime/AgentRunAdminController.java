package com.webchat.adminapi.agent.runtime;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/agent-runs")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class AgentRunAdminController {

    private final AgentRunAdminService runAdminService;

    public AgentRunAdminController(AgentRunAdminService runAdminService) {
        this.runAdminService = runAdminService;
    }

    public record DecisionRequest(String decision, Long boundChannelId, String note) {}

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID targetUserId
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Map<String, Object>>) authError;
        return ApiResponse.ok(runAdminService.listRuns(page, size, status, targetUserId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("id") UUID runId
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Map<String, Object>>) authError;
        try {
            return ApiResponse.ok(runAdminService.getRun(runId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<Map<String, Object>> decide(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("id") UUID runId,
            @RequestBody(required = false) DecisionRequest request
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Map<String, Object>>) authError;
        try {
            String decision = request != null ? request.decision : null;
            Long boundChannelId = request != null ? request.boundChannelId : null;
            String note = request != null ? request.note : null;
            return ApiResponse.ok(runAdminService.decide(userId, runId, decision, boundChannelId, note));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        return null;
    }
}

