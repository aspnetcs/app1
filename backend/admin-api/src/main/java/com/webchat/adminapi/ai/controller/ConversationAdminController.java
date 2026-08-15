package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/conversations")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class ConversationAdminController {

    private final boolean conversationPinStarEnabled;
    private final boolean conversationManagementEnabled;
    private final boolean messageVersioningEnabled;

    public ConversationAdminController(
            @Value("${platform.conversation-pin-star.enabled:true}") boolean conversationPinStarEnabled,
            @Value("${platform.conversation-management.enabled:true}") boolean conversationManagementEnabled,
            @Value("${platform.message-versioning.enabled:false}") boolean messageVersioningEnabled
    ) {
        this.conversationPinStarEnabled = conversationPinStarEnabled;
        this.conversationManagementEnabled = conversationManagementEnabled;
        this.messageVersioningEnabled = messageVersioningEnabled;
    }

    @GetMapping("/pin-star-config")
    public ApiResponse<Map<String, Object>> pinStarConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        return ApiResponse.ok(Map.of(
                "enabled", conversationPinStarEnabled,
                "pinEnabled", conversationPinStarEnabled,
                "starEnabled", conversationPinStarEnabled,
                "fields", List.of("pinned", "starred"),
                "temporaryConversationAllowed", false,
                "managementEnabled", conversationManagementEnabled,
                "renameEnabled", conversationManagementEnabled,
                "archiveEnabled", conversationManagementEnabled,
                "restoreEnabled", conversationManagementEnabled
        ));
    }

    @GetMapping("/message-version-config")
    public ApiResponse<Map<String, Object>> messageVersionConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        return ApiResponse.ok(Map.of(
                "enabled", messageVersioningEnabled,
                "rootKey", "parent_message_id",
                "versionKey", "version",
                "supportsRestoreFlow", true
        ));
    }
}
