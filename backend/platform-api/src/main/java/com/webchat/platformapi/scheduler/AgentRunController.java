package com.webchat.platformapi.scheduler;

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
@RequestMapping("/api/v1/agent-runs")
@ConditionalOnProperty(name = "platform.agent-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AgentRunController {

    private final AgentRunService runService;

    public AgentRunController(AgentRunService runService) {
        this.runService = runService;
    }

    public record CreateRequest(UUID agentId, Long requestedChannelId) {}

    public record FailRequest(String errorMessage) {}

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody CreateRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(runService.createRun(userId, request != null ? request.agentId : null, request != null ? request.requestedChannelId : null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        return ApiResponse.ok(runService.listRuns(userId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID runId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(runService.getRun(userId, runId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping("/{id}/start")
    public ApiResponse<Map<String, Object>> start(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID runId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(runService.startRun(userId, runId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID runId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(runService.completeRun(userId, runId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping("/{id}/fail")
    public ApiResponse<Map<String, Object>> fail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID runId,
            @RequestBody(required = false) FailRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            String message = request != null ? request.errorMessage : null;
            return ApiResponse.ok(runService.failRun(userId, runId, message));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }
}

