package com.webchat.platformapi.ai.conversation;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private static final Comparator<AiConversationEntity> CONVERSATION_LIST_ORDER = Comparator
            .comparing(AiConversationEntity::isPinned)
            .reversed()
            .thenComparing(
                    ConversationPinOrderSupport::resolvePinnedSortKey,
                    Comparator.nullsLast(Comparator.naturalOrder())
            )
            .thenComparing(AiConversationEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final AiConversationRepository convRepo;
    private final AiMessageRepository msgRepo;
    private final AiMessageBlockService messageBlockService;
    private final AiConversationForkService forkService;
    private final AuditService auditService;
    private final boolean temporaryChatEnabled;
    private final boolean conversationPinStarEnabled;
    private final boolean conversationManagementEnabled;

    @Autowired
    public ConversationController(
            AiConversationRepository convRepo,
            AiMessageRepository msgRepo,
            AiMessageBlockService messageBlockService,
            AiConversationForkService forkService,
            AuditService auditService,
            @Value("${platform.temporary-chat.enabled:false}") boolean temporaryChatEnabled,
            @Value("${platform.conversation-pin-star.enabled:true}") boolean conversationPinStarEnabled,
            @Value("${platform.conversation-management.enabled:true}") boolean conversationManagementEnabled
    ) {
        this.convRepo = convRepo;
        this.msgRepo = msgRepo;
        this.messageBlockService = messageBlockService;
        this.forkService = forkService;
        this.auditService = auditService;
        this.temporaryChatEnabled = temporaryChatEnabled;
        this.conversationPinStarEnabled = conversationPinStarEnabled;
        this.conversationManagementEnabled = conversationManagementEnabled;
    }

    ConversationController(
            AiConversationRepository convRepo,
            AiMessageRepository msgRepo,
            AiConversationForkService forkService,
            AuditService auditService,
            boolean temporaryChatEnabled,
            boolean conversationPinStarEnabled,
            boolean conversationManagementEnabled
    ) {
        this(convRepo, msgRepo, null, forkService, auditService, temporaryChatEnabled,
                conversationPinStarEnabled, conversationManagementEnabled);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        List<AiConversationEntity> convs = normalizeConversationOrder(
                convRepo.findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId)
        );
        Map<UUID, Long> messageCounts = loadMessageCounts(convs);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiConversationEntity c : convs) {
            out.add(toConversationDto(c, messageCounts.getOrDefault(c.getId(), 0L)));
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/archived")
    public ApiResponse<List<Map<String, Object>>> archived(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!conversationManagementEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation management is disabled");
        }

        List<AiConversationEntity> convs = convRepo.findByUserIdAndDeletedAtIsNotNullAndTemporaryFalseOrderByDeletedAtDesc(userId);
        Map<UUID, Long> messageCounts = loadMessageCounts(convs);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiConversationEntity c : convs) {
            out.add(toConversationDto(c, messageCounts.getOrDefault(c.getId(), 0L)));
        }
        return ApiResponse.ok(out);
    }
    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");

        AiConversationEntity conv = new AiConversationEntity();
        conv.setUserId(userId);
        Boolean requestedTemporary = null;
        if (body != null) {
            conv.setTitle(str(body.get("title")));
            conv.setModel(str(body.get("model")));
            conv.setCompareModelsJson(toJsonArray(body.containsKey("compareModels") ? body.get("compareModels") : body.get("compare_models")));
            conv.setSystemPrompt(str(body.get("system_prompt")));
            conv.setMode(normalizeConversationMode(
                    body.get("mode"),
                    conv.getCompareModelsJson(),
                    conv.getMode()
            ));
            conv.setCaptainSelectionMode(normalizeCaptainSelectionMode(
                    body.containsKey("captainSelectionMode")
                            ? body.get("captainSelectionMode")
                            : body.get("captain_selection_mode")
            ));
            requestedTemporary = bool(body.get("isTemporary"));
            if (requestedTemporary == null) {
                requestedTemporary = bool(body.get("is_temporary"));
            }
        }
        boolean isTemporary = Boolean.TRUE.equals(requestedTemporary);
        if (isTemporary && !temporaryChatEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "temporary chat is disabled");
        }
        conv.setTemporary(isTemporary);
        if (conv.getTitle() == null || conv.getTitle().isBlank()) {
            conv.setTitle("新对话");
        }

        if (isTemporary) {
            Instant now = Instant.now();
            conv.setId(UUID.randomUUID());
            conv.setCreatedAt(now);
            conv.setUpdatedAt(now);
            auditService.log(
                    userId,
                    "conversation.create",
                    Map.of(
                            "conversationId", String.valueOf(conv.getId()),
                            "isTemporary", true
                    ),
                    request == null ? null : request.getRemoteAddr(),
                    request == null ? null : request.getHeader("User-Agent")
            );
            return ApiResponse.ok(toConversationDto(conv, 0));
        }

        AiConversationEntity saved = convRepo.save(conv);
        auditService.log(
                userId,
                "conversation.create",
                Map.of(
                        "conversationId", String.valueOf(saved.getId()),
                        "isTemporary", false
                ),
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : request.getHeader("User-Agent")
        );
        return ApiResponse.ok(toConversationDto(saved, 0));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> messages(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");

        AiConversationEntity conv = convRepo.findById(id).orElse(null);
        if (conv == null || conv.getDeletedAt() != null || !conv.getUserId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation not found");
        }

        List<AiMessageEntity> msgs = msgRepo.findByConversationIdAndParentMessageIdIsNullOrderByCreatedAtAsc(id);
        Map<UUID, List<Map<String, Object>>> blocksByMessageId = messageBlockService == null
                ? Collections.emptyMap()
                : messageBlockService.listBlocks(msgs.stream().map(AiMessageEntity::getId).filter(Objects::nonNull).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMessageEntity m : msgs) {
            out.add(toMsgDto(m, blocksByMessageId.getOrDefault(m.getId(), List.of())));
        }
        return ApiResponse.ok(out);
    }

    @PostMapping("/{id}/fork")
    public ApiResponse<Map<String, Object>> forkConversation(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "request body is required");
        if (!forkService.isEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation fork is disabled");
        }

        UUID messageId = null;
        Object messageIdRaw = body.get("messageId");
        if (messageIdRaw instanceof UUID uuid) {
            messageId = uuid;
        } else if (messageIdRaw != null) {
            String messageIdText = str(messageIdRaw);
            if (messageIdText != null) {
                try {
                    messageId = UUID.fromString(messageIdText);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (messageId == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "messageId is required");
        }

        try {
            AiConversationEntity forked = forkService.forkConversation(id, messageId, userId);
            auditService.log(
                    userId,
                    "conversation.fork",
                    Map.of(
                            "sourceConversationId", String.valueOf(id),
                            "sourceMessageId", String.valueOf(messageId),
                            "forkConversationId", String.valueOf(forked.getId())
                    ),
                    request == null ? null : request.getRemoteAddr(),
                    request == null ? null : request.getHeader("User-Agent")
            );
            return ApiResponse.ok(toConversationDto(forked, msgRepo.countByConversationIdAndParentMessageIdIsNull(forked.getId())));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, ex.getMessage());
        }
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<Map<String, Object>> addMessage(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "request body is required");

        AiConversationEntity conv = convRepo.findById(id).orElse(null);
        if (conv == null || conv.getDeletedAt() != null || !conv.getUserId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation not found");
        }

        String role = str(body.get("role"));
        String content = str(body.get("content"));
        if (role == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "missing parameter: role");
        if (!"user".equals(role) && !"assistant".equals(role) && !"system".equals(role)) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "invalid role: " + role);
        }

        AiMessageEntity msg = new AiMessageEntity();
        msg.setConversationId(id);
        msg.setRole(role);
        msg.setContent(content);
        if (body.get("content_type") != null) msg.setContentType(str(body.get("content_type")));
        if (body.get("media_url") != null) msg.setMediaUrl(str(body.get("media_url")));
        if (body.get("model") != null) msg.setModel(str(body.get("model")));
        UUID multiRoundId = parseUuid(body.get("multi_round_id"));
        if (multiRoundId != null) msg.setMultiRoundId(multiRoundId);
        Integer branchIndex = intValue(body.get("branch_index"));
        if (branchIndex != null) msg.setBranchIndex(Math.max(0, branchIndex));
        if (body.get("token_count") instanceof Number n) msg.setTokenCount(n.intValue());
        if (body.get("channel_id") instanceof Number n) msg.setChannelId(n.longValue());
        UUID parentMessageId = parseUuid(body.get("parent_message_id"));
        if (parentMessageId != null) {
            AiMessageEntity parent = msgRepo.findByIdAndConversationId(parentMessageId, id).orElse(null);
            if (parent == null) {
                return ApiResponse.error(ErrorCodes.SERVER_ERROR, "parent message not found");
            }
            UUID rootMessageId = parent.getParentMessageId() != null ? parent.getParentMessageId() : parent.getId();
            AiMessageEntity root = msgRepo.findByIdAndConversationId(rootMessageId, id).orElse(null);
            if (root == null) {
                return ApiResponse.error(ErrorCodes.SERVER_ERROR, "parent message not found");
            }
            msg.setParentMessageId(root.getId());
            msg.setVersion(resolveNextVersion(root, intValue(body.get("version"))));
        } else {
            Integer requestedVersion = intValue(body.get("version"));
            msg.setVersion(requestedVersion == null || requestedVersion < 1 ? 1 : requestedVersion);
        }

        AiMessageEntity saved = msgRepo.save(msg);
        List<Map<String, Object>> blocks = resolveBlocksForSave(msg, body.get("blocks"));
        if (messageBlockService != null) {
            messageBlockService.replaceBlocks(id, saved.getId(), msg.getRole(), blocks);
        }

        // Auto-set title from first user message
        if ("user".equals(role) && "新对话".equals(conv.getTitle()) && content != null && !content.isBlank()) {
            conv.setTitle(content.length() > 50 ? content.substring(0, 50) + "..." : content);
        }
        // Always save to bump updatedAt (JPA @PreUpdate) so conversation appears at top
        convRepo.save(conv);

        return ApiResponse.ok(toMsgDto(saved, blocks));
    }

    @PutMapping("/{id}/pin")
    public ApiResponse<Map<String, Object>> pinConversation(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!conversationPinStarEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation pin/star is disabled");
        }
        return updatePinStar(userId, id, body, "pinned", AiConversationEntity::setPinned);
    }

    @PutMapping("/{id}/star")
    public ApiResponse<Map<String, Object>> starConversation(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!conversationPinStarEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation pin/star is disabled");
        }
        return updatePinStar(userId, id, body, "starred", AiConversationEntity::setStarred);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!conversationManagementEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation management is disabled");
        }

        AiConversationEntity conv = convRepo.findById(id).orElse(null);
        if (conv == null || conv.getDeletedAt() != null || !conv.getUserId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation not found");
        }

        if (body != null) {
            if (body.containsKey("title")) {
                String title = str(body.get("title"));
                if (title == null) {
                    return ApiResponse.error(ErrorCodes.PARAM_MISSING, "title is required");
                }
                conv.setTitle(title);
            }
            if (body.containsKey("model")) conv.setModel(str(body.get("model")));
            if (body.containsKey("compareModels") || body.containsKey("compare_models")) {
                conv.setCompareModelsJson(toJsonArray(body.containsKey("compareModels") ? body.get("compareModels") : body.get("compare_models")));
                conv.setMode(normalizeConversationMode(null, conv.getCompareModelsJson(), conv.getMode()));
            }
            if (body.containsKey("system_prompt")) conv.setSystemPrompt(str(body.get("system_prompt")));
            if (body.containsKey("mode")) {
                conv.setMode(normalizeConversationMode(body.get("mode"), conv.getCompareModelsJson(), conv.getMode()));
            }
            if (body.containsKey("captainSelectionMode") || body.containsKey("captain_selection_mode")) {
                conv.setCaptainSelectionMode(normalizeCaptainSelectionMode(
                        body.containsKey("captainSelectionMode")
                                ? body.get("captainSelectionMode")
                                : body.get("captain_selection_mode")
                ));
            }
        }

        AiConversationEntity saved = convRepo.save(conv);
        return ApiResponse.ok(toConversationDto(saved, msgRepo.countByConversationIdAndParentMessageIdIsNull(saved.getId())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!conversationManagementEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation management is disabled");
        }

        AiConversationEntity conv = convRepo.findById(id).orElse(null);
        if (conv == null || conv.getDeletedAt() != null || !conv.getUserId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation not found");
        }

        conv.setDeletedAt(Instant.now());
        convRepo.save(conv);
        return ApiResponse.ok("archived", null);
    }

    @PutMapping("/{id}/restore")
    public ApiResponse<Map<String, Object>> restore(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID id
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!conversationManagementEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation management is disabled");
        }

        AiConversationEntity conv = convRepo.findById(id).orElse(null);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation not found");
        }
        if (conv.getDeletedAt() == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation is not archived");
        }

        conv.setDeletedAt(null);
        conv.setUpdatedAt(Instant.now());
        AiConversationEntity saved = convRepo.save(conv);
        return ApiResponse.ok(toConversationDto(saved, msgRepo.countByConversationIdAndParentMessageIdIsNull(saved.getId())));
    }

    private Map<UUID, Long> loadMessageCounts(List<AiConversationEntity> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> ids = conversations.stream()
                .map(AiConversationEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, Long> counts = new HashMap<>();
        for (AiMessageRepository.ConversationMessageCount row : msgRepo.countByConversationIds(ids)) {
            if (row.getConversationId() == null) continue;
            counts.put(row.getConversationId(), row.getMessageCount());
        }
        return counts;
    }

    private Map<String, Object> toConversationDto(AiConversationEntity c, long messageCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        String resolvedMode = normalizeConversationMode(c.getMode(), c.getCompareModelsJson(), c.getMode());
        String resolvedCaptainSelectionMode = normalizeCaptainSelectionMode(c.getCaptainSelectionMode());
        m.put("id", c.getId());
        m.put("title", c.getTitle());
        m.put("model", c.getModel());
        m.put("mode", resolvedMode);
        m.put("compareModels", parseJsonList(c.getCompareModelsJson()));
        m.put("compare_models", parseJsonList(c.getCompareModelsJson()));
        m.put("captainMode", resolvedCaptainSelectionMode);
        m.put("captain_selection_mode", resolvedCaptainSelectionMode);
        m.put("system_prompt", c.getSystemPrompt());
        m.put("pinned", c.isPinned());
        m.put("starred", c.isStarred());
        m.put("is_temporary", c.isTemporary());
        m.put("messageCount", messageCount);
        m.put("message_count", messageCount);
        m.put("created_at", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());
        m.put("updated_at", c.getUpdatedAt() == null ? "" : c.getUpdatedAt().toString());
        m.put("pinned_at", c.getPinnedAt() == null ? null : c.getPinnedAt().toString());
        m.put("deleted_at", c.getDeletedAt() == null ? null : c.getDeletedAt().toString());
        m.put("source_conversation_id", c.getSourceConversationId());
        m.put("source_message_id", c.getSourceMessageId());
        return m;
    }

    private static Map<String, Object> toMsgDto(AiMessageEntity m, List<Map<String, Object>> blocks) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("role", m.getRole());
        map.put("content", m.getContent());
        map.put("content_type", m.getContentType());
        map.put("media_url", m.getMediaUrl());
        map.put("parent_message_id", m.getParentMessageId());
        map.put("multi_round_id", m.getMultiRoundId());
        map.put("branch_index", m.getBranchIndex());
        map.put("version", m.getVersion());
        map.put("token_count", m.getTokenCount());
        map.put("model", m.getModel());
        map.put("blocks", blocks == null ? List.of() : blocks);
        map.put("created_at", m.getCreatedAt() == null ? "" : m.getCreatedAt().toString());
        return map;
    }

    private List<Map<String, Object>> resolveBlocksForSave(AiMessageEntity message, Object rawBlocks) {
        if (messageBlockService == null) {
            return List.of();
        }
        return messageBlockService.buildBlocksFromMessage(
                message.getContent(),
                message.getContentType(),
                message.getMediaUrl(),
                rawBlocks
        );
    }

    private ApiResponse<Map<String, Object>> updatePinStar(
            UUID userId,
            UUID id,
            Map<String, Object> body,
            String paramKey,
            BiConsumer<AiConversationEntity, Boolean> mutator
    ) {
        if (body == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, paramKey + " is required");
        }
        Boolean value = bool(body.get(paramKey));
        if (value == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, paramKey + " is required");
        }
        AiConversationEntity conv = convRepo.findById(id).orElse(null);
        if (conv == null || conv.getDeletedAt() != null || !conv.getUserId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "conversation not found");
        }
        if (conv.isTemporary()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "temporary conversations cannot be modified");
        }
        Object currentValue = "pinned".equals(paramKey) ? conv.isPinned() : conv.isStarred();
        if (Objects.equals(currentValue, value)) {
            return ApiResponse.ok(toConversationDto(conv, msgRepo.countByConversationIdAndParentMessageIdIsNull(conv.getId())));
        }
        if ("pinned".equals(paramKey)) {
            conv.setPinnedAt(Boolean.TRUE.equals(value) ? Instant.now() : null);
        }
        mutator.accept(conv, value);
        AiConversationEntity saved = convRepo.save(conv);
        return ApiResponse.ok(toConversationDto(saved, msgRepo.countByConversationIdAndParentMessageIdIsNull(saved.getId())));
    }

    private List<AiConversationEntity> normalizeConversationOrder(List<AiConversationEntity> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return List.of();
        }
        List<AiConversationEntity> normalized = new ArrayList<>(conversations);
        normalized.sort(CONVERSATION_LIST_ORDER);
        return normalized;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Boolean bool(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        String text = str(value);
        if (text == null) return null;
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        try {
            return Boolean.parseBoolean(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer resolveNextVersion(AiMessageEntity root, Integer requestedVersion) {
        int maxVersion = root.getVersion() == null || root.getVersion() < 1 ? 1 : root.getVersion();
        for (AiMessageEntity child : msgRepo.findByParentMessageIdOrderByVersionAsc(root.getId())) {
            if (child.getVersion() != null && child.getVersion() > maxVersion) {
                maxVersion = child.getVersion();
            }
        }
        if (requestedVersion == null || requestedVersion <= maxVersion) {
            return maxVersion + 1;
        }
        return requestedVersion;
    }

    private static UUID parseUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Integer intValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonHolder.OBJECT_MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String toJsonArray(Object value) {
        try {
            List<String> output = new ArrayList<>();
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    String text = str(item);
                    if (text != null) output.add(text);
                }
            } else if (value != null) {
                String text = str(value);
                if (text != null) {
                    for (String part : text.split(",")) {
                        String normalized = str(part);
                        if (normalized != null) output.add(normalized);
                    }
                }
            }
            return JsonHolder.OBJECT_MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String normalizeConversationMode(Object rawMode, String compareModelsJson, String fallbackMode) {
        String mode = str(rawMode);
        if ("team".equalsIgnoreCase(mode)) {
            return "team";
        }
        if ("compare".equalsIgnoreCase(mode)) {
            return "compare";
        }
        if ("chat".equalsIgnoreCase(mode)) {
            return "chat";
        }
        String fallback = str(fallbackMode);
        if ("team".equalsIgnoreCase(fallback)) {
            return "team";
        }
        if (parseJsonList(compareModelsJson).size() > 1) {
            return "compare";
        }
        if ("compare".equalsIgnoreCase(fallback)) {
            return "compare";
        }
        return "chat";
    }

    private static String normalizeCaptainSelectionMode(Object rawMode) {
        String mode = str(rawMode);
        if ("fixed_first".equalsIgnoreCase(mode)) {
            return "fixed_first";
        }
        if ("auto".equalsIgnoreCase(mode)) {
            return "auto";
        }
        return null;
    }

    private static final class JsonHolder {
        private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    }

}

