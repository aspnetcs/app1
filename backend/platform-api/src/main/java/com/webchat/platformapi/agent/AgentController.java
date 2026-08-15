package com.webchat.platformapi.agent;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.auth.group.UserGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-facing Agent Market API.
 * Replaces ConversationTemplateController + AgentMarketController + prompt user API.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;

    public AgentController(
            AgentService agentService,
            UserGroupService userGroupService,
            @Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.agentService = agentService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    /**
     * List public agents in the marketplace.
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMarket(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(name = "category", required = false) String category
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "agents")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "agents not allowed by group policy");
        }
        return ApiResponse.ok(agentService.listMarket(category));
    }

    /**
     * List user's private agents.
     */
    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> listMine(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        return ApiResponse.ok(agentService.listUserAgents(userId));
    }

    /**
     * Get a single agent detail.
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getAgent(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(agentService.getAgent(userId, id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "agent not found");
        }
    }

    /**
     * Create a personal agent.
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createAgent(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(agentService.createAgent(userId, body));
        } catch (IllegalArgumentException e) {
            log.warn("[agent] create rejected for user {}: {}", userId, e.getMessage());
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /**
     * Update an owned agent.
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateAgent(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(agentService.updateAgent(userId, id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /**
     * Delete an owned agent (soft delete).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAgent(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            agentService.deleteAgent(userId, id);
            return ApiResponse.ok("ok", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /**
     * Install (clone) a public agent to user's collection.
     */
    @PostMapping("/{id}/install")
    public ApiResponse<Map<String, Object>> installAgent(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(agentService.installAgent(userId, id));
        } catch (IllegalArgumentException e) {
            log.warn("[agent] install rejected for user {}: {}", userId, e.getMessage());
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }
}
