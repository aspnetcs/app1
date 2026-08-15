package com.webchat.platformapi.ai.conversation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AiMessageBlockService {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "text", "citation", "file", "image", "translation", "error"
    );

    private final AiMessageBlockRepository repository;
    private final ObjectMapper objectMapper;

    public AiMessageBlockService(AiMessageBlockRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void replaceBlocks(UUID conversationId,
                              UUID messageId,
                              String role,
                              List<Map<String, Object>> blocks) {
        repository.deleteByMessageId(messageId);
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        List<AiMessageBlockEntity> entities = new ArrayList<>(blocks.size());
        int index = 0;
        for (Map<String, Object> block : blocks) {
            Map<String, Object> normalized = normalizeBlock(block, index++);
            AiMessageBlockEntity entity = new AiMessageBlockEntity();
            entity.setConversationId(conversationId);
            entity.setMessageId(messageId);
            entity.setRole(normalizeRole(role));
            entity.setBlockType(String.valueOf(normalized.get("type")));
            entity.setBlockKey(String.valueOf(normalized.get("key")));
            entity.setSequenceNo(((Number) normalized.get("sequence")).intValue());
            entity.setStatus(String.valueOf(normalized.get("status")));
            entity.setPayloadJson(writeJson(normalized.get("payload")));
            entities.add(entity);
        }
        repository.saveAll(entities);
    }

    public List<Map<String, Object>> buildBlocksFromMessage(String content,
                                                            String contentType,
                                                            String mediaUrl,
                                                            Object rawBlocks) {
        List<Map<String, Object>> explicit = extractExplicitBlocks(rawBlocks);
        if (!explicit.isEmpty()) {
            return explicit;
        }

        List<Map<String, Object>> generated = new ArrayList<>();
        String normalizedType = normalizeContentType(contentType, mediaUrl);
        if (content != null && !content.isBlank()) {
            generated.add(block("text", "text-0", 0, "final", Map.of("text", content)));
        }
        if (mediaUrl != null && !mediaUrl.isBlank()) {
            String blockType = "image".equals(normalizedType) ? "image" : "file";
            generated.add(block(blockType, blockType + "-0", generated.size(), "final", Map.of("url", mediaUrl)));
        }
        return generated;
    }

    public List<Map<String, Object>> listBlocks(UUID messageId) {
        if (messageId == null) {
            return List.of();
        }
        return toDtoList(repository.findByMessageIdOrderBySequenceNoAscCreatedAtAsc(messageId));
    }

    public Map<UUID, List<Map<String, Object>>> listBlocks(Collection<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (AiMessageBlockEntity entity : repository.findByMessageIdInOrderByMessageIdAscSequenceNoAscCreatedAtAsc(messageIds)) {
            result.computeIfAbsent(entity.getMessageId(), ignored -> new ArrayList<>()).add(toDto(entity));
        }
        return result;
    }

    @Transactional
    public void copyBlocks(UUID sourceMessageId, UUID targetMessageId, UUID conversationId, String role) {
        List<Map<String, Object>> blocks = listBlocks(sourceMessageId);
        replaceBlocks(conversationId, targetMessageId, role, blocks);
    }

    private List<Map<String, Object>> extractExplicitBlocks(Object rawBlocks) {
        if (!(rawBlocks instanceof Collection<?> items)) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        int index = 0;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            normalized.add(normalizeBlock(map, index++));
        }
        return normalized;
    }

    private Map<String, Object> normalizeBlock(Map<String, Object> raw, int fallbackIndex) {
        String type = stringValue(raw.get("type"));
        if (type == null) {
            type = stringValue(raw.get("blockType"));
        }
        if (type == null || !SUPPORTED_TYPES.contains(type)) {
            type = "text";
        }

        String key = stringValue(raw.get("key"));
        if (key == null) {
            key = stringValue(raw.get("blockKey"));
        }
        if (key == null) {
            key = type + "-" + fallbackIndex;
        }

        Integer sequence = integerValue(raw.get("sequence"));
        if (sequence == null) {
            sequence = integerValue(raw.get("sequenceNo"));
        }
        if (sequence == null || sequence < 0) {
            sequence = fallbackIndex;
        }

        String status = stringValue(raw.get("status"));
        if (status == null) {
            status = "final";
        }

        Object rawPayload = raw.get("payload");
        Map<String, Object> payload = rawPayload instanceof Map<?, ?> payloadMap
                ? copyMap(payloadMap)
                : extractPayloadFromLooseBlock(raw, type);

        return block(type, key, sequence, status, payload);
    }

    private Map<String, Object> extractPayloadFromLooseBlock(Map<String, Object> raw, String type) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (Set.of("type", "blockType", "key", "blockKey", "status", "sequence", "sequenceNo").contains(key)) {
                continue;
            }
            payload.put(key, entry.getValue());
        }
        if (payload.isEmpty() && "text".equals(type)) {
            payload.put("text", "");
        }
        return payload;
    }

    private List<Map<String, Object>> toDtoList(List<AiMessageBlockEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> blocks = new ArrayList<>(entities.size());
        for (AiMessageBlockEntity entity : entities) {
            blocks.add(toDto(entity));
        }
        return blocks;
    }

    private Map<String, Object> toDto(AiMessageBlockEntity entity) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("message_id", entity.getMessageId());
        map.put("conversation_id", entity.getConversationId());
        map.put("role", entity.getRole());
        map.put("type", entity.getBlockType());
        map.put("key", entity.getBlockKey());
        map.put("sequence", entity.getSequenceNo());
        map.put("status", entity.getStatus());
        map.put("payload", readPayload(entity.getPayloadJson()));
        map.put("created_at", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        map.put("updated_at", entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString());
        return map;
    }

    private Object readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) {
            return Map.of("raw", payloadJson);
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> block(String type,
                                             String key,
                                             int sequence,
                                             String status,
                                             Map<String, Object> payload) {
        LinkedHashMap<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.put("key", key);
        block.put("sequence", sequence);
        block.put("status", status);
        block.put("payload", payload == null ? Map.of() : payload);
        return block;
    }

    private static Map<String, Object> copyMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    private static String normalizeRole(String role) {
        return role == null || role.isBlank() ? "assistant" : role;
    }

    private static String normalizeContentType(String contentType, String mediaUrl) {
        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.trim().toLowerCase();
            if (SUPPORTED_TYPES.contains(normalized)) {
                return normalized;
            }
        }
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return "text";
        }
        String lower = mediaUrl.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp")) {
            return "image";
        }
        return "file";
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public Set<String> supportedTypes() {
        return new LinkedHashSet<>(SUPPORTED_TYPES);
    }
}
