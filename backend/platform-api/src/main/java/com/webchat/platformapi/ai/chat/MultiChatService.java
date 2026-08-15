package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-model chat service.
 * Supports two modes:
 *   - parallel: same question sent to multiple models simultaneously
 *   - multi-agent discussion: multiple agents discuss a topic in turns
 */
@Service
public class MultiChatService {

    private static final String DISCUSSION_SESSION_PREFIX = "multi-chat:discussion:session:";
    private static final Duration DISCUSSION_SESSION_TTL = Duration.ofHours(6);

    private final AiChatService aiChatService;
    private final AgentService agentService;
    private final MultiChatRuntimeConfigService runtimeConfigService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ChatStreamContextRegistry streamContextRegistry;
    private final ChatSkillContextService chatSkillContextService;
    private final ChatKnowledgeContextService chatKnowledgeContextService;

    public MultiChatService(
            AiChatService aiChatService,
            AgentService agentService,
            MultiChatRuntimeConfigService runtimeConfigService,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            ChatStreamContextRegistry streamContextRegistry,
            ChatSkillContextService chatSkillContextService,
            ChatKnowledgeContextService chatKnowledgeContextService
    ) {
        this.aiChatService = aiChatService;
        this.agentService = agentService;
        this.runtimeConfigService = runtimeConfigService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.streamContextRegistry = streamContextRegistry;
        this.chatSkillContextService = chatSkillContextService;
        this.chatKnowledgeContextService = chatKnowledgeContextService;
    }

    // ==================== Parallel Mode ====================

    public MultiChatResult start(UUID userId, String traceId, Map<String, Object> requestBody, List<String> models, String ip, String userAgent) {
        String roundId = UUID.randomUUID().toString();
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> uniqueModels = new LinkedHashSet<>(models);
        List<ChatStreamTask> tasks = new ArrayList<>();

        for (String model : uniqueModels) {
            String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> req = new HashMap<>();
            if (requestBody != null) req.putAll(requestBody);
            req.put("model", model);
            tasks.add(new ChatStreamTask(requestId, traceId, req));
            streamContextRegistry.registerStart(userId, requestId, traceId, model);
            items.add(Map.of(
                    "requestId", requestId,
                    "model", model,
                    "roundId", roundId
            ));
        }

        if (!aiChatService.startStreams(userId, tasks, ip, userAgent)) {
            throw new IllegalStateException("service is busy");
        }

        return new MultiChatResult(roundId, items);
    }

    // ==================== Multi-Agent Discussion Mode ====================

    public boolean isMultiAgentDiscussionEnabled() {
        return runtimeConfigService.snapshot().discussionEnabled();
    }

    public int getMultiAgentDiscussionMaxAgents() {
        return runtimeConfigService.snapshot().discussionMaxAgents();
    }

    public int getMultiAgentDiscussionMaxRounds() {
        return runtimeConfigService.snapshot().discussionMaxRounds();
    }

    public boolean isParallelEnabled() {
        return runtimeConfigService.snapshot().parallelEnabled();
    }

    public int getParallelMaxModels() {
        return runtimeConfigService.snapshot().parallelMaxModels();
    }

    public DiscussionSession startMultiAgentDiscussion(UUID userId, List<UUID> agentIds, String topic, List<String> knowledgeBaseIds) {
        MultiChatRuntimeConfigService.Snapshot config = runtimeConfigService.snapshot();
        if (agentIds == null || agentIds.size() < 2) {
            throw new IllegalArgumentException("at least two agents are required");
        }
        if (agentIds.size() > config.discussionMaxAgents()) {
            throw new IllegalArgumentException("too many agents");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("topic is required");
        }

        List<UUID> uniqueAgentIds = new ArrayList<>(new LinkedHashSet<>(agentIds));
        List<AgentEntity> loadedAgents = agentService.loadAgents(uniqueAgentIds);
        if (loadedAgents.size() != uniqueAgentIds.size()) {
            throw new IllegalArgumentException("invalid agentId");
        }

        List<DiscussionAgent> agents = new ArrayList<>();
        for (AgentEntity agent : loadedAgents) {
            agents.add(new DiscussionAgent(
                    agent.getId(),
                    agent.getName(),
                    agent.getAvatar(),
                    agent.getSystemPrompt(),
                    agent.getModelId()
            ));
        }

        String sessionId = UUID.randomUUID().toString();
        DiscussionSession session = new DiscussionSession(
                sessionId, topic.trim(), List.copyOf(agents), 0, config.discussionMaxRounds(), normalizeKnowledgeBaseIds(knowledgeBaseIds)
        );
        saveSessionHolder(sessionId, new SessionHolder(session, userId));
        return session;
    }

    public ChatStreamTask buildNextDiscussionTurn(
            UUID userId,
            String traceId,
            DiscussionSession session,
            int agentIndex,
            List<Map<String, Object>> conversationHistory
    ) {
        int completedTurns = countCompletedDiscussionTurns(conversationHistory);
        if (completedTurns >= session.maxRounds()) {
            throw new IllegalStateException("max rounds reached");
        }
        if (agentIndex < 0 || agentIndex >= session.agents().size()) {
            throw new IllegalArgumentException("invalid agent index");
        }

        DiscussionAgent agent = session.agents().get(agentIndex);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", buildDiscussionSystemPrompt(agent, session.topic(), session.agents())
        ));
        if (conversationHistory != null) {
            messages.addAll(conversationHistory);
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", agent.modelId());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        if (chatSkillContextService != null) {
            chatSkillContextService.applySavedSkillContracts(userId, requestBody);
        }
        if (session.knowledgeBaseIds() != null) {
            requestBody.put("knowledgeBaseIds", session.knowledgeBaseIds());
        }
        if (chatKnowledgeContextService != null) {
            chatKnowledgeContextService.applyKnowledgeContext(userId, requestBody);
        }
        return new ChatStreamTask(
                "discussion_" + UUID.randomUUID().toString().replace("-", ""),
                traceId,
                requestBody
        );
    }

    private int countCompletedDiscussionTurns(List<Map<String, Object>> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return 0;
        }
        int completedTurns = 0;
        for (Map<String, Object> item : conversationHistory) {
            if (item == null) {
                continue;
            }
            Object role = item.get("role");
            if (role != null && "assistant".equalsIgnoreCase(String.valueOf(role).trim())) {
                completedTurns++;
            }
        }
        return completedTurns;
    }

    public DiscussionSession getDiscussionSessionForUser(String sessionId, UUID userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        SessionHolder holder = loadSessionHolder(sessionId);
        if (!holder.owner().equals(userId)) {
            throw new IllegalStateException("session does not belong to user");
        }
        return holder.session();
    }

    public void completeDiscussionRound(String sessionId, UUID userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            return;
        }
        SessionHolder holder;
        try {
            holder = loadSessionHolder(sessionId);
        } catch (IllegalStateException e) {
            return;
        }
        if (!holder.owner().equals(userId)) {
            return;
        }
        int nextRound = holder.session().currentRound() + 1;
        if (nextRound >= holder.session().maxRounds()) {
            redis.delete(sessionKey(sessionId));
            return;
        }
        DiscussionSession updated = new DiscussionSession(
                holder.session().sessionId(),
                holder.session().topic(),
                holder.session().agents(),
                nextRound,
                holder.session().maxRounds(),
                holder.session().knowledgeBaseIds()
        );
        saveSessionHolder(sessionId, new SessionHolder(updated, holder.owner()));
    }

    private String buildDiscussionSystemPrompt(DiscussionAgent currentAgent, String topic, List<DiscussionAgent> allAgents) {
        StringBuilder sb = new StringBuilder();
        if (currentAgent.systemPrompt() != null && !currentAgent.systemPrompt().isBlank()) {
            sb.append(currentAgent.systemPrompt()).append("\n\n");
        }
        sb.append("You are participating in a multi-agent discussion.\n");
        sb.append("Your agent identity: ").append(currentAgent.name()).append("\n");
        sb.append("Topic: ").append(topic).append("\n");
        sb.append("Other agents: ");
        boolean first = true;
        for (DiscussionAgent agent : allAgents) {
            if (agent.agentId().equals(currentAgent.agentId())) continue;
            if (!first) sb.append(", ");
            sb.append(agent.name());
            first = false;
        }
        sb.append("\n");
        sb.append("Stay consistent with your configured persona, keep the discussion moving, and keep each reply concise.");
        return sb.toString();
    }

    // ==================== Records ====================

    public record MultiChatResult(String roundId, List<Map<String, Object>> items) {}

    public record DiscussionAgent(UUID agentId, String name, String avatar, String systemPrompt, String modelId) {}

    public record DiscussionSession(String sessionId, String topic, List<DiscussionAgent> agents, int currentRound, int maxRounds,
                                    List<String> knowledgeBaseIds) {}

    static record SessionHolder(DiscussionSession session, UUID owner) {}

    private void saveSessionHolder(String sessionId, SessionHolder holder) {
        try {
            redis.opsForValue().set(sessionKey(sessionId), objectMapper.writeValueAsString(holder), DISCUSSION_SESSION_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to persist discussion session", e);
        }
    }

    private SessionHolder loadSessionHolder(String sessionId) {
        String key = sessionKey(sessionId);
        String payload = redis.opsForValue().get(key);
        if (payload == null || payload.isBlank()) {
            throw new IllegalStateException("session not found");
        }
        try {
            redis.expire(key, DISCUSSION_SESSION_TTL);
            return objectMapper.readValue(payload, SessionHolder.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to load discussion session", e);
        }
    }

    private static String sessionKey(String sessionId) {
        return DISCUSSION_SESSION_PREFIX + sessionId;
    }

    private List<String> normalizeKnowledgeBaseIds(List<String> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String knowledgeBaseId : knowledgeBaseIds) {
            if (knowledgeBaseId == null) {
                continue;
            }
            String text = knowledgeBaseId.trim();
            if (!text.isEmpty()) {
                unique.add(text);
            }
        }
        return List.copyOf(unique);
    }
}
