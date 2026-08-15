package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.ai.conversation.AiConversationEntity;
import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.ai.conversation.AiMessageEntity;
import com.webchat.platformapi.ai.conversation.AiMessageRepository;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/messages")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class MessageVersionAdminController {

    private final AiMessageRepository messageRepository;
    private final AiConversationRepository conversationRepository;
    private final boolean messageVersioningEnabled;

    public MessageVersionAdminController(
            AiMessageRepository messageRepository,
            AiConversationRepository conversationRepository,
            @Value("${platform.message-versioning.enabled:false}") boolean messageVersioningEnabled
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.messageVersioningEnabled = messageVersioningEnabled;
    }

    @GetMapping("/version-config")
    public ApiResponse<Map<String, Object>> versionConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        return ApiResponse.ok(Map.of(
                "enabled", messageVersioningEnabled,
                "supportsManualRestore", true,
                "notes", List.of(
                        "Persisted ai_message version chains can be queried and restored as a new latest version.",
                        "Only server-side persisted version chains are exposed in admin."
                )
        ));
    }

    @PostMapping("/{parentId}/restore")
    public ApiResponse<Map<String, Object>> restoreVersion(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("parentId") UUID parentId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        if (!messageVersioningEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "message versioning is disabled");
        }
        if (body == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "version is required");
        }

        Integer targetVersion = parseVersion(body.get("version"));
        if (targetVersion == null || targetVersion < 1) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "version is required");
        }

        AiMessageEntity parent = messageRepository.findById(parentId).orElse(null);
        if (parent == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "message not found");
        }

        AiConversationEntity conversation = conversationRepository.findById(parent.getConversationId()).orElse(null);
        if (conversation == null || conversation.getDeletedAt() != null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation not available");
        }

        List<AiMessageEntity> versions = new ArrayList<>();
        versions.add(parent);
        for (AiMessageEntity candidate : messageRepository.findByParentMessageIdOrderByVersionAsc(parentId)) {
            if (candidate.getId() == null || candidate.getId().equals(parentId)) continue;
            versions.add(candidate);
        }
        versions.sort(Comparator.comparingInt(item -> item.getVersion() == null ? 0 : item.getVersion()));

        AiMessageEntity target = versions.stream()
                .filter(item -> item.getVersion() != null && item.getVersion() == targetVersion)
                .findFirst()
                .orElse(null);
        if (target == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "target version not found");
        }

        int nextVersion = versions.stream()
                .map(AiMessageEntity::getVersion)
                .filter(version -> version != null && version > 0)
                .max(Integer::compareTo)
                .orElse(1) + 1;

        AiMessageEntity restored = new AiMessageEntity();
        restored.setConversationId(target.getConversationId());
        restored.setRole(target.getRole());
        restored.setContent(target.getContent());
        restored.setContentType(target.getContentType());
        restored.setMediaUrl(target.getMediaUrl());
        restored.setTokenCount(target.getTokenCount());
        restored.setModel(target.getModel());
        restored.setChannelId(target.getChannelId());
        restored.setParentMessageId(parentId);
        restored.setVersion(nextVersion);

        AiMessageEntity saved = messageRepository.save(restored);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
        return ApiResponse.ok(toVersionDto(saved));
    }

    private static Integer parseVersion(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<String, Object> toVersionDto(AiMessageEntity message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", message.getId());
        map.put("role", message.getRole());
        map.put("content", message.getContent());
        map.put("content_type", message.getContentType());
        map.put("media_url", message.getMediaUrl());
        map.put("parent_message_id", message.getParentMessageId());
        map.put("version", message.getVersion());
        map.put("token_count", message.getTokenCount());
        map.put("model", message.getModel());
        map.put("created_at", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
        return map;
    }
}



