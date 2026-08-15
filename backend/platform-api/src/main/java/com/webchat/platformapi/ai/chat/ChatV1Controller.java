package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.filter.RequestIdFilter;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.credits.CreditsErrorSupport;
import com.webchat.platformapi.credits.CreditsSystemConfig;
import com.webchat.platformapi.trace.TraceService;

import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.role.RolePolicyService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatV1Controller {

    private static final Logger log = LoggerFactory.getLogger(ChatV1Controller.class);

    private final ChatStreamStarter chatStreamStarter;
    private final AuditService auditService;
    private final UserGroupService userGroupService;
    private final RolePolicyService rolePolicyService;
    private final MultiChatService multiChatService;
    private final StringRedisTemplate redis;
    private final AgentService agentService;
    private final ChatMcpToolContextService chatMcpToolContextService;
    private final ChatSkillContextService chatSkillContextService;
    private final ChatKnowledgeContextService chatKnowledgeContextService;
    private final ObjectMapper objectMapper;
    private final TraceService traceService;
    private final ChatStreamContextRegistry streamContextRegistry;
    private final ChatAttachmentPreprocessor attachmentPreprocessor;
    private final String defaultModel;
    private final boolean temporaryChatEnabled;
    private final boolean userGroupsEnabled;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @Nullable
    private CreditsSystemConfig creditsSystemConfig;

    public ChatV1Controller(
            ChatStreamStarter chatStreamStarter,
            AuditService auditService,
            UserGroupService userGroupService,
            RolePolicyService rolePolicyService,
            MultiChatService multiChatService,
            StringRedisTemplate redis,
            AgentService agentService,
            ChatMcpToolContextService chatMcpToolContextService,
            ChatSkillContextService chatSkillContextService,
            ChatKnowledgeContextService chatKnowledgeContextService,
            ObjectMapper objectMapper,
            @Nullable TraceService traceService,
            ChatStreamContextRegistry streamContextRegistry,
            ChatAttachmentPreprocessor attachmentPreprocessor,
            @Value("${ai.default-model:}") String defaultModel,
            @Value("${platform.temporary-chat.enabled:false}") boolean temporaryChatEnabled,
            @Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.chatStreamStarter = chatStreamStarter;
        this.auditService = auditService;
        this.userGroupService = userGroupService;
        this.rolePolicyService = rolePolicyService;
        this.multiChatService = multiChatService;
        this.redis = redis;
        this.agentService = agentService;
        this.chatMcpToolContextService = chatMcpToolContextService;
        this.chatSkillContextService = chatSkillContextService;
        this.chatKnowledgeContextService = chatKnowledgeContextService;
        this.objectMapper = objectMapper;
        this.traceService = traceService == null ? TraceService.noop() : traceService;
        this.streamContextRegistry = streamContextRegistry;
        this.attachmentPreprocessor = attachmentPreprocessor;
        this.defaultModel = defaultModel == null ? "" : defaultModel.trim();
        this.temporaryChatEnabled = temporaryChatEnabled;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @PostMapping("/completions")
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestAttribute(name = RequestIdFilter.TRACE_ATTR_KEY, required = false) String traceId,
            @RequestBody(required = false) ChatRequestBody body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");

        Object reqIdAttr = request.getAttribute(com.webchat.platformapi.common.filter.RequestIdFilter.ATTR_KEY);
        String requestId = reqIdAttr != null ? String.valueOf(reqIdAttr) : "req_" + UUID.randomUUID().toString().replace("-", "");
        String normalizedTraceId = traceId == null ? "" : traceId.trim();
        if (normalizedTraceId.isEmpty()) {
            normalizedTraceId = UUID.randomUUID().toString().replace("-", "");
        }

        Map<String, Object> req = body == null ? new HashMap<>() : body.toRequestMap();
        req.put("stream", true);
        boolean isTemporary = parseTemporary(req);
        req.remove("isTemporary");
        req.remove("is_temporary");
        ApiResponse<Map<String, Object>> maskError = applyMaskToolNames(userId, req);
        if (maskError != null) return maskError;
        chatMcpToolContextService.applySavedMcpToolNames(userId, req);
        if (userGroupsEnabled && isTemporary && !userGroupService.isFeatureAllowed(userId, "temporary_chat")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "temporary chat not allowed by group policy");
        }
        if (userGroupsEnabled && req.containsKey("toolNames") && !userGroupService.isFeatureAllowed(userId, "function_calling")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "function calling not allowed by group policy");
        }

        if (isTemporary && !temporaryChatEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "temporary chat is disabled");
        }

        if (!hasModel(req)) {
            if (defaultModel != null && !defaultModel.isBlank()) req.put("model", defaultModel);
            else return ApiResponse.error(ErrorCodes.PARAM_MISSING, "missing model");
        }

        String model = String.valueOf(req.getOrDefault("model", "")).trim();
        String effectiveRole = normalizeStreamingRole(role);
        req.put("__userRole", effectiveRole);
        if (shouldEnforceRoleModelPolicy()) {
            java.util.Set<String> allowed = rolePolicyService.resolveAllowedModels(userId, effectiveRole);
            if (!allowed.isEmpty() && !allowed.contains(model)) {
                return CreditsErrorSupport.apiError("model_not_allowed");
            }
        }
        if (userGroupsEnabled && !checkGroupChatRateLimit(userId)) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "已达到分组会话频率限制，请稍后再试");
        }

        // Preprocess attachments: resolve files, extract document text, convert images
        var attachmentResult = attachmentPreprocessor.process(userId, req);
        if (!attachmentResult.success()) {
            return ApiResponse.error(ErrorCodes.PARAM_INVALID, attachmentResult.error());
        }
        req = attachmentResult.processedRequest();
        chatSkillContextService.applySavedSkillContracts(userId, req);
        chatKnowledgeContextService.applyKnowledgeContext(userId, req);

        String ip = RequestUtils.clientIp(request);
        String ua = RequestUtils.userAgent(request);

        // Register early so WS clients can abort immediately after receiving requestId/traceId.
        streamContextRegistry.registerStart(userId, requestId, normalizedTraceId, model);
        traceService.recordSpan(userId, normalizedTraceId, requestId, null, "chat.completions.start",
                java.time.Instant.now(), null, "started", Map.of(
                        "model", model,
                        "messageCount", messageCount(req),
                        "isTemporary", isTemporary
                ));

        boolean accepted;
        try {
            accepted = chatStreamStarter.startStream(userId, requestId, normalizedTraceId, req, ip, ua);
        } catch (Exception e) {
            log.error("[chat] chatStreamStarter.startStream failed: userId={}, requestId={}", userId, requestId, e);
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "internal server error");
        }

        if (!accepted) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "service is busy");
        }

        auditService.log(userId, isTemporary ? "temporary_chat" : "chat.request", Map.of(
                "requestId", requestId,
                "traceId", normalizedTraceId,
                "model", model,
                "hasImages", hasImages(req),
                "messageCount", messageCount(req),
                "isTemporary", isTemporary
        ), ip, ua);

        return ApiResponse.ok(Map.of("requestId", requestId, "traceId", normalizedTraceId));
    }

    @PostMapping("/completions/multi")
    public ApiResponse<Map<String, Object>> createMulti(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestAttribute(name = RequestIdFilter.TRACE_ATTR_KEY, required = false) String traceId,
            @RequestBody(required = false) ChatRequestBody body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!multiChatService.isParallelEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "multi chat is disabled");
        }
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "multi_chat")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "multi chat not allowed by group policy");
        }

        Map<String, Object> req = body == null ? new HashMap<>() : body.toRequestMap();
        String normalizedTraceId = traceId == null ? "" : traceId.trim();
        if (normalizedTraceId.isEmpty()) {
            normalizedTraceId = UUID.randomUUID().toString().replace("-", "");
        }
        List<String> models = normalizeStringList(body == null ? null : body.getModels());
        if (models.size() < 2) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "at least two models are required");
        if (models.size() > multiChatService.getParallelMaxModels()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "too many models");
        }

        boolean isTemporary = parseTemporary(req);
        req.remove("isTemporary");
        req.remove("is_temporary");
        ApiResponse<Map<String, Object>> maskError = applyMaskToolNames(userId, req);
        if (maskError != null) return maskError;
        chatMcpToolContextService.applySavedMcpToolNames(userId, req);
        if (userGroupsEnabled && isTemporary && !userGroupService.isFeatureAllowed(userId, "temporary_chat")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "temporary chat not allowed by group policy");
        }
        if (userGroupsEnabled && req.containsKey("toolNames") && !userGroupService.isFeatureAllowed(userId, "function_calling")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "function calling not allowed by group policy");
        }
        if (isTemporary && !temporaryChatEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "temporary chat is disabled");
        }

        String ip = RequestUtils.clientIp(request);
        String ua = RequestUtils.userAgent(request);
        req.put("stream", true);
        String effectiveRole = normalizeStreamingRole(role);
        req.put("__userRole", effectiveRole);
        req.remove("models");

        var attachmentResult = attachmentPreprocessor.process(userId, req);
        if (!attachmentResult.success()) {
            return ApiResponse.error(ErrorCodes.PARAM_INVALID, attachmentResult.error());
        }
        req = attachmentResult.processedRequest();

        chatSkillContextService.applySavedSkillContracts(userId, req);
        chatKnowledgeContextService.applyKnowledgeContext(userId, req);

        if (shouldEnforceRoleModelPolicy()) {
            java.util.Set<String> allowedMulti = rolePolicyService.resolveAllowedModels(userId, effectiveRole);
            if (!allowedMulti.isEmpty()) {
                for (String model : models) {
                    if (!allowedMulti.contains(model)) {
                        return CreditsErrorSupport.apiError("model_not_allowed");
                    }
                }
            }
        }
        if (userGroupsEnabled && !checkGroupChatRateLimit(userId)) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "\u5df2\u8fbe\u5230\u5206\u7ec4\u4f1a\u8bdd\u9891\u7387\u9650\u5236\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
        }

        try {
            MultiChatService.MultiChatResult result = multiChatService.start(userId, normalizedTraceId, req, models, ip, ua);
            traceService.recordSpan(userId, normalizedTraceId, result.roundId(), null, "chat.completions.multi.start",
                    java.time.Instant.now(), null, "started", Map.of(
                            "models", models,
                            "messageCount", messageCount(req),
                            "isTemporary", isTemporary
                    ));
            auditService.log(userId, "chat.request.multi", Map.of(
                    "roundId", result.roundId(),
                    "traceId", normalizedTraceId,
                    "models", models,
                    "messageCount", messageCount(req),
                    "isTemporary", isTemporary
            ), ip, ua);
            return ApiResponse.ok(Map.of(
                    "roundId", result.roundId(),
                    "traceId", normalizedTraceId,
                    "items", result.items()
            ));
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "service is busy");
        } catch (Exception e) {
            log.error("[chat] multiChatService.start failed: userId={}", userId, e);
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "internal server error");
        }
    }

    private static boolean hasModel(Map<String, Object> req) {
        if (req == null) return false;
        Object value = req.get("model");
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private static int messageCount(Map<String, Object> req) {
        if (req == null) return 0;
        Object messages = req.get("messages");
        if (messages instanceof java.util.Collection<?> c) return c.size();
        return 0;
    }

    private static boolean hasImages(Map<String, Object> req) {
        if (req == null) return false;
        Object messages = req.get("messages");
        if (!(messages instanceof java.util.Collection<?> c)) return false;
        for (Object item : c) {
            if (item instanceof Map<?, ?> map) {
                Object images = map.get("images");
                if (images instanceof java.util.Collection<?> ic && !ic.isEmpty()) return true;
            }
        }
        return false;
    }

    private static boolean parseTemporary(Map<String, Object> req) {
        if (req == null) return false;
        Object raw = req.containsKey("isTemporary") ? req.get("isTemporary") : req.get("is_temporary");
        if (raw == null) return false;
        if (raw instanceof Boolean b) return b;
        if (raw instanceof Number n) return n.intValue() != 0;
        String text = String.valueOf(raw).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String text = value.trim();
            if (!text.isEmpty()) unique.add(text);
        }
        return List.copyOf(unique);
    }

    private boolean checkGroupChatRateLimit(UUID userId) {
        UserGroupService.GroupProfile profile = userGroupService.resolveProfile(userId);
        Integer limit = profile.chatRateLimitPerMinute();
        if (limit == null || limit <= 0) {
            return true;
        }
        String bucket = String.valueOf(System.currentTimeMillis() / 60000L);
        String key = "group_chat_rate:" + userId + ":" + bucket;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(2));
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("[chat] group rate-limit check failed, fallback allow: userId={}, error={}", userId, e.toString());
            return true;
        }
    }

    private ApiResponse<Map<String, Object>> applyMaskToolNames(UUID userId, Map<String, Object> req) {
        if (req == null || req.containsKey("toolNames")) {
            return null;
        }
        UUID agentId = parseMaskId(req.get("maskId"));
        if (agentId == null) {
            agentId = parseMaskId(req.get("mask_id"));
        }
        if (agentId == null) {
            agentId = parseMaskId(req.get("agentId"));
        }
        if (agentId == null) {
            agentId = parseMaskId(req.get("agent_id"));
        }
        if (agentId == null) {
            return null;
        }

        try {
            AgentEntity agent = agentService.getAgentEntity(agentId);
            List<String> toolNames = parseToolNames(agent.getRequiredToolsJson());
            if (!toolNames.isEmpty()) {
                req.put("toolNames", toolNames);
            }
        } catch (IllegalArgumentException e) {
            log.debug("[chat] agent {} not found for tool resolution: {}", agentId, e.getMessage());
        }
        return null;
    }

    private UUID parseMaskId(Object raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseToolNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return normalizeToolNames(values);
        } catch (Exception e) {
            log.debug("[chat.v1] parse toolNames as json failed, fallback csv: {}", e.toString());
            return normalizeToolNames(List.of(raw.split(",")));
        }
    }

    private static List<String> normalizeToolNames(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String text = value.trim();
            if (!text.isEmpty()) unique.add(text);
        }
        return new ArrayList<>(unique);
    }

    // ==================== Multi-Agent Discussion Endpoints ====================

    @GetMapping("/multi-agent-discussion/config")
    public ApiResponse<Map<String, Object>> multiAgentDiscussionConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        boolean allowed = multiChatService.isMultiAgentDiscussionEnabled()
                && (!userGroupsEnabled || userGroupService.isFeatureAllowed(userId, "multi_agent_discussion"));
        return ApiResponse.ok(Map.of(
                "enabled", allowed,
                "maxAgents", multiChatService.getMultiAgentDiscussionMaxAgents(),
                "maxRounds", multiChatService.getMultiAgentDiscussionMaxRounds()
        ));
    }

    @PostMapping("/multi-agent-discussion/start")
    public ApiResponse<Map<String, Object>> multiAgentDiscussionStart(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody MultiAgentDiscussionStartRequest body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!multiChatService.isMultiAgentDiscussionEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "multi-agent discussion is disabled");
        }
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "multi_agent_discussion")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "multi-agent discussion not allowed by group policy");
        }
        List<String> rawIds = normalizeStringList(body == null ? null : body.agentIds());
        if (rawIds.isEmpty()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "agentIds is required");
        }
        List<UUID> agentIds = new ArrayList<>();
        for (String rawId : rawIds) {
            try {
                agentIds.add(UUID.fromString(rawId));
            } catch (Exception e) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "invalid agentId");
            }
        }
        ApiResponse<Map<String, Object>> discussionModelError = validateDiscussionModels(userId, role, agentIds);
        if (discussionModelError != null) {
            return discussionModelError;
        }
        String topic = body == null || body.topic() == null ? "" : body.topic();
        List<String> knowledgeBaseIds = normalizeStringList(body == null ? null : body.knowledgeBaseIds());
        try {
            MultiChatService.DiscussionSession session = multiChatService.startMultiAgentDiscussion(userId, agentIds, topic, knowledgeBaseIds);
            List<Map<String, Object>> agents = session.agents().stream().map(agent -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("agentId", agent.agentId());
                item.put("name", agent.name());
                item.put("avatar", agent.avatar());
                item.put("modelId", agent.modelId());
                return item;
            }).toList();
            return ApiResponse.ok(Map.of(
                    "sessionId", session.sessionId(),
                    "topic", session.topic(),
                    "maxRounds", session.maxRounds(),
                    "agents", agents
            ));
        } catch (IllegalArgumentException e) {
            log.warn("[multi-agent-discussion] start rejected for user {}: {}", userId, e.getMessage());
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "multi-agent discussion parameters are invalid");
        }
    }

    @PostMapping("/multi-agent-discussion/next")
    public ApiResponse<Map<String, Object>> multiAgentDiscussionNext(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestAttribute(name = RequestIdFilter.TRACE_ATTR_KEY, required = false) String traceId,
            @RequestBody MultiAgentDiscussionNextRequest body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!multiChatService.isMultiAgentDiscussionEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "multi-agent discussion is disabled");
        }
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "multi_agent_discussion")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "multi-agent discussion not allowed by group policy");
        }

        String normalizedTraceId = traceId == null ? "" : traceId.trim();
        if (normalizedTraceId.isEmpty()) {
            normalizedTraceId = UUID.randomUUID().toString().replace("-", "");
        }

        String sessionId = body == null || body.sessionId() == null ? null : body.sessionId().trim();
        if (sessionId == null || sessionId.isEmpty()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "sessionId is required");
        }
        int agentIndex;
        try {
            agentIndex = parseAgentIndex(body == null ? null : body.agentIndex());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "agentIndex is invalid");
        }
        List<Map<String, Object>> history = body == null || body.history() == null
                ? null
                : new ArrayList<>(body.history());

        try {
            MultiChatService.DiscussionSession session = multiChatService.getDiscussionSessionForUser(sessionId, userId);
            ChatStreamTask task = multiChatService.buildNextDiscussionTurn(userId, normalizedTraceId, session, agentIndex, history);
            Map<String, Object> taskRequestBody = task.requestBody() == null
                    ? new HashMap<>()
                    : new HashMap<>(task.requestBody());
            taskRequestBody.put("__userRole", normalizeStreamingRole(role));
            ChatStreamTask streamingTask = new ChatStreamTask(task.requestId(), task.traceId(), taskRequestBody);
            String taskModel = String.valueOf(taskRequestBody.getOrDefault("model", "")).trim();
            streamContextRegistry.registerStart(userId, task.requestId(), normalizedTraceId, taskModel);
            String clientIp = RequestUtils.clientIp(request);
            String userAgent = RequestUtils.userAgent(request);
            if (clientIp == null || clientIp.isBlank()) clientIp = "unknown";
            if (userAgent == null || userAgent.isBlank()) userAgent = "unknown";
            boolean started = chatStreamStarter.startStreams(userId, List.of(streamingTask), clientIp, userAgent);
            if (!started) {
                return ApiResponse.error(ErrorCodes.RATE_LIMIT, "service is busy");
            }
            return ApiResponse.ok(Map.of(
                    "requestId", task.requestId(),
                    "traceId", normalizedTraceId,
                    "agentIndex", agentIndex,
                    "agentName", session.agents().get(agentIndex).name(),
                    "agentAvatar", session.agents().get(agentIndex).avatar()
            ));
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
                log.warn("[multi-agent-discussion] next rejected for user {} session {}: {}", userId, sessionId, e.getMessage());
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "invalid or expired session");
            }
            log.error("[multi-agent-discussion] next turn failed for user {} session {}", userId, sessionId, e);
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "multi-agent discussion turn failed");
        }
    }

    private static int parseAgentIndex(JsonNode raw) {
        if (raw == null || raw.isNull()) return 0;
        if (raw.isIntegralNumber()) return raw.intValue();
        if (raw.isTextual()) {
            String trimmed = raw.textValue() == null ? "" : raw.textValue().trim();
            if (trimmed.isEmpty()) return 0;
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("agentIndex");
            }
        }
        throw new IllegalArgumentException("agentIndex");
    }

    private ApiResponse<Map<String, Object>> validateDiscussionModels(UUID userId, String role, List<UUID> agentIds) {
        if (!shouldEnforceRoleModelPolicy()) {
            return null;
        }
        java.util.Set<String> allowedModels = rolePolicyService.resolveAllowedModels(userId, normalizeStreamingRole(role));
        if (allowedModels.isEmpty()) {
            return null;
        }
        List<UUID> uniqueAgentIds = new ArrayList<>(new LinkedHashSet<>(agentIds));
        List<AgentEntity> agents = agentService.loadAgents(uniqueAgentIds);
        if (agents.size() != uniqueAgentIds.size()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "invalid agentId");
        }
        for (AgentEntity agent : agents) {
            String modelId = agent.getModelId() == null ? "" : agent.getModelId().trim();
            if (!modelId.isEmpty() && !allowedModels.contains(modelId)) {
                return CreditsErrorSupport.apiError("model_not_allowed");
            }
        }
        return null;
    }

    private boolean shouldEnforceRoleModelPolicy() {
        return creditsSystemConfig == null || !creditsSystemConfig.isFreeModeEnabled();
    }

    private static String normalizeStreamingRole(@Nullable String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        return "pending".equals(normalized) ? "user" : normalized;
    }
}
