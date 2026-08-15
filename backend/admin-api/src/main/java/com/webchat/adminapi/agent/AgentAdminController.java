package com.webchat.adminapi.agent;

import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin Agent management API.
 * Replaces ConversationTemplateAdminController + PromptAdminController + AgentMarketAdminController.
 */
@RestController
@RequestMapping("/api/v1/admin/agents")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class AgentAdminController {

    private final AgentService agentService;

    public AgentAdminController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Boolean featured
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return cast(authError);
        try {
            var pageResult = agentService.listForAdmin(search, category, enabled, scope, featured, page, size);
            return ApiResponse.ok(Map.of(
                    "items", pageResult.getContent(),
                    "total", pageResult.getTotalElements(),
                    "page", pageResult.getNumber(),
                    "size", pageResult.getSize()
            ));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return cast(authError);
        try {
            return ApiResponse.ok(agentService.createForAdmin(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return cast(authError);
        try {
            return ApiResponse.ok(agentService.updateForAdmin(id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable UUID id
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return castVoid(authError);
        try {
            agentService.deleteForAdmin(id);
            return ApiResponse.ok("ok", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static ApiResponse<Map<String, Object>> cast(ApiResponse<?> value) {
        return (ApiResponse<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static ApiResponse<Void> castVoid(ApiResponse<?> value) {
        return (ApiResponse<Void>) value;
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        return null;
    }
}
