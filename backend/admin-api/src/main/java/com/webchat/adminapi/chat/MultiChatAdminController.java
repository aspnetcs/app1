package com.webchat.adminapi.chat;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.config.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/multi-chat")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class MultiChatAdminController {

    private final MultiChatAdminService multiChatAdminService;

    @Autowired
    public MultiChatAdminController(MultiChatAdminService multiChatAdminService) {
        this.multiChatAdminService = multiChatAdminService;
    }

    // Kept for standalone tests.
    public MultiChatAdminController(
            @Value("${platform.multi-chat.enabled:false}") boolean parallelEnabled,
            @Value("${platform.multi-chat.max-models:3}") int maxModels,
            @Value("${platform.multi-chat.multi-agent-discussion.enabled:false}") boolean multiAgentDiscussionEnabledDefault,
            @Value("${platform.multi-chat.multi-agent-discussion.max-agents:4}") int multiAgentDiscussionMaxAgentsDefault,
            @Value("${platform.multi-chat.multi-agent-discussion.max-rounds:20}") int multiAgentDiscussionMaxRoundsDefault,
            SysConfigService sysConfigService
    ) {
        this(new MultiChatAdminService(
                parallelEnabled,
                maxModels,
                multiAgentDiscussionEnabledDefault,
                multiAgentDiscussionMaxAgentsDefault,
                multiAgentDiscussionMaxRoundsDefault,
                sysConfigService
        ));
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }
        return ApiResponse.ok(multiChatAdminService.parallelConfig());
    }

    @GetMapping("/multi-agent-discussion/config")
    public ApiResponse<Map<String, Object>> multiAgentDiscussionConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        return ApiResponse.ok(multiChatAdminService.multiAgentDiscussionConfig());
    }

    @PutMapping("/multi-agent-discussion/config")
    public ApiResponse<Map<String, Object>> updateMultiAgentDiscussionConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        return multiChatAdminService.updateMultiAgentDiscussionConfig(body);
    }
}
