package com.webchat.platformapi.ai.conversation;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageVersionController {

    private final AiMessageRepository messageRepository;
    private final AiConversationRepository conversationRepository;
    private final AiMessageBlockService messageBlockService;
    private final boolean messageVersioningEnabled;

    @Autowired
    public MessageVersionController(
            AiMessageRepository messageRepository,
            AiConversationRepository conversationRepository,
            AiMessageBlockService messageBlockService,
            @Value("${platform.message-versioning.enabled:false}") boolean messageVersioningEnabled
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.messageBlockService = messageBlockService;
        this.messageVersioningEnabled = messageVersioningEnabled;
    }

    MessageVersionController(
            AiMessageRepository messageRepository,
            AiConversationRepository conversationRepository,
            boolean messageVersioningEnabled
    ) {
        this(messageRepository, conversationRepository, null, messageVersioningEnabled);
    }

    @GetMapping("/{parentId}/versions")
    public ApiResponse<List<Map<String, Object>>> versions(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("parentId") String parentIdText
    ) {
        if (!messageVersioningEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "message versioning is disabled");
        }
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }

        UUID messageId;
        try {
            messageId = UUID.fromString(parentIdText);
        } catch (IllegalArgumentException ignored) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "message not available");
        }

        AiMessageEntity current = messageRepository.findById(messageId).orElse(null);
        if (current == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "message not available");
        }

        UUID rootMessageId = current.getParentMessageId() != null ? current.getParentMessageId() : current.getId();
        AiMessageEntity parent = messageRepository.findById(rootMessageId).orElse(null);
        if (parent == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "message not available");
        }

        AiConversationEntity conversation = conversationRepository.findById(parent.getConversationId()).orElse(null);
        if (conversation == null || conversation.getDeletedAt() != null || !conversation.getUserId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "message not available");
        }

        List<AiMessageEntity> childVersions = messageRepository.findByParentMessageIdOrderByVersionAsc(rootMessageId);
        List<AiMessageEntity> allVersions = new ArrayList<>();
        allVersions.add(parent);
        for (AiMessageEntity version : childVersions) {
            if (rootMessageId.equals(version.getId())) continue;
            allVersions.add(version);
        }

        allVersions.sort(Comparator.comparingInt(m -> m.getVersion() == null ? 0 : m.getVersion()));
        Map<UUID, List<Map<String, Object>>> blocksByMessageId = messageBlockService == null
                ? Map.of()
                : messageBlockService.listBlocks(allVersions.stream().map(AiMessageEntity::getId).filter(Objects::nonNull).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMessageEntity message : allVersions) {
            out.add(toVersionDto(message, blocksByMessageId.getOrDefault(message.getId(), List.of())));
        }

        return ApiResponse.ok(out);
    }

    private static Map<String, Object> toVersionDto(AiMessageEntity message, List<Map<String, Object>> blocks) {
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
        map.put("blocks", blocks == null ? List.of() : blocks);
        map.put("created_at", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
        return map;
    }
}
