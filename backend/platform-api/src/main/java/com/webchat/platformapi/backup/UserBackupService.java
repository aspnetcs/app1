package com.webchat.platformapi.backup;

import com.webchat.platformapi.ai.conversation.AiConversationEntity;
import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.ai.conversation.AiMessageBlockEntity;
import com.webchat.platformapi.ai.conversation.AiMessageBlockRepository;
import com.webchat.platformapi.ai.conversation.AiMessageEntity;
import com.webchat.platformapi.ai.conversation.AiMessageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserBackupService {

    public static final int SCHEMA_VERSION = 1;

    private static final Pattern FILE_ID_PATTERN = Pattern.compile("\\bfile_[0-9a-fA-F]{32}\\b");

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiMessageBlockRepository blockRepository;

    public UserBackupService(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            AiMessageBlockRepository blockRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
    }

    public Map<String, Object> exportUserPayload(UUID userId, List<String> modules, UserBackupLimits limits) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        List<String> normalizedModules = modules == null ? List.of() : List.copyOf(modules);

        List<AiConversationEntity> conversations = conversationRepository
                .findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId);

        if (conversations.size() > limits.conversationLimit()) {
            conversations = conversations.subList(0, limits.conversationLimit());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        Set<String> fileIds = new LinkedHashSet<>();

        if (normalizedModules.contains("conversations")) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiConversationEntity conv : conversations) {
                items.add(toConversationDto(conv));
            }
            data.put("conversations", items);
        }

        Map<UUID, List<AiMessageEntity>> messagesByConversation = new LinkedHashMap<>();
        if (normalizedModules.contains("messages") || normalizedModules.contains("messageBlocks")) {
            for (AiConversationEntity conv : conversations) {
                List<AiMessageEntity> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
                if (messages.size() > limits.messageLimitPerConversation()) {
                    messages = messages.subList(Math.max(0, messages.size() - limits.messageLimitPerConversation()), messages.size());
                }
                messagesByConversation.put(conv.getId(), messages);
                for (AiMessageEntity message : messages) {
                    collectFileIds(fileIds, message.getContent());
                    collectFileIds(fileIds, message.getMediaUrl());
                }
            }
        }

        if (normalizedModules.contains("messages")) {
            Map<String, Object> messageMap = new LinkedHashMap<>();
            for (Map.Entry<UUID, List<AiMessageEntity>> entry : messagesByConversation.entrySet()) {
                List<Map<String, Object>> items = entry.getValue().stream().map(UserBackupService::toMessageDto).toList();
                messageMap.put(entry.getKey().toString(), items);
            }
            data.put("messages", messageMap);
        }

        if (normalizedModules.contains("messageBlocks")) {
            List<UUID> messageIds = new ArrayList<>();
            for (List<AiMessageEntity> items : messagesByConversation.values()) {
                for (AiMessageEntity message : items) {
                    messageIds.add(message.getId());
                }
            }

            Map<String, Object> blockMap = new LinkedHashMap<>();
            if (!messageIds.isEmpty()) {
                List<AiMessageBlockEntity> blocks = blockRepository.findByMessageIdInOrderByMessageIdAscSequenceNoAscCreatedAtAsc(messageIds);
                Map<UUID, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
                for (AiMessageBlockEntity block : blocks) {
                    grouped.computeIfAbsent(block.getMessageId(), ignored -> new ArrayList<>()).add(toBlockDto(block));
                    collectFileIds(fileIds, block.getPayloadJson());
                }
                for (Map.Entry<UUID, List<Map<String, Object>>> entry : grouped.entrySet()) {
                    blockMap.put(entry.getKey().toString(), entry.getValue());
                }
            }
            data.put("messageBlocks", blockMap);
        }

        List<Map<String, Object>> fileRefs = fileIds.stream()
                .map(fileId -> Map.<String, Object>of("fileId", fileId))
                .toList();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("conversationCount", conversations.size());
        meta.put("fileRefCount", fileRefs.size());
        meta.put("limits", Map.of(
                "conversationLimit", limits.conversationLimit(),
                "messageLimitPerConversation", limits.messageLimitPerConversation()
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "user_backup");
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("exportedAt", Instant.now().toString());
        payload.put("modules", normalizedModules.isEmpty() ? List.of("conversations", "messages") : normalizedModules);
        payload.put("data", data);
        payload.put("fileRefs", fileRefs);
        payload.put("meta", meta);
        return payload;
    }

    private static Map<String, Object> toConversationDto(AiConversationEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId() == null ? "" : entity.getId().toString());
        item.put("title", entity.getTitle() == null ? "" : entity.getTitle());
        item.put("model", entity.getModel() == null ? "" : entity.getModel());
        item.put("compareModelsJson", entity.getCompareModelsJson() == null ? "[]" : entity.getCompareModelsJson());
        item.put("systemPrompt", entity.getSystemPrompt() == null ? "" : entity.getSystemPrompt());
        item.put("pinned", entity.isPinned());
        item.put("starred", entity.isStarred());
        item.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        item.put("updatedAt", entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString());
        return item;
    }

    private static Map<String, Object> toMessageDto(AiMessageEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId() == null ? "" : entity.getId().toString());
        item.put("conversationId", entity.getConversationId() == null ? "" : entity.getConversationId().toString());
        item.put("role", entity.getRole() == null ? "" : entity.getRole());
        item.put("contentType", entity.getContentType() == null ? "" : entity.getContentType());
        item.put("content", entity.getContent() == null ? "" : entity.getContent());
        item.put("mediaUrl", entity.getMediaUrl() == null ? "" : entity.getMediaUrl());
        item.put("model", entity.getModel() == null ? "" : entity.getModel());
        item.put("channelId", entity.getChannelId() == null ? 0L : entity.getChannelId());
        item.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        item.put("parentMessageId", entity.getParentMessageId() == null ? "" : entity.getParentMessageId().toString());
        item.put("multiRoundId", entity.getMultiRoundId() == null ? "" : entity.getMultiRoundId().toString());
        item.put("branchIndex", entity.getBranchIndex() == null ? 0 : entity.getBranchIndex());
        item.put("version", entity.getVersion() == null ? 1 : entity.getVersion());
        return item;
    }

    private static Map<String, Object> toBlockDto(AiMessageBlockEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId() == null ? "" : entity.getId().toString());
        item.put("messageId", entity.getMessageId() == null ? "" : entity.getMessageId().toString());
        item.put("conversationId", entity.getConversationId() == null ? "" : entity.getConversationId().toString());
        item.put("role", entity.getRole() == null ? "" : entity.getRole());
        item.put("blockType", entity.getBlockType() == null ? "" : entity.getBlockType());
        item.put("blockKey", entity.getBlockKey() == null ? "" : entity.getBlockKey());
        item.put("sequenceNo", entity.getSequenceNo() == null ? 0 : entity.getSequenceNo());
        item.put("status", entity.getStatus() == null ? "" : entity.getStatus());
        item.put("payloadJson", entity.getPayloadJson() == null ? "{}" : entity.getPayloadJson());
        item.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        item.put("updatedAt", entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString());
        return item;
    }

    private static void collectFileIds(Set<String> sink, String text) {
        if (sink == null || text == null || text.isBlank()) return;
        Matcher matcher = FILE_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            sink.add(matcher.group());
        }
    }
}
