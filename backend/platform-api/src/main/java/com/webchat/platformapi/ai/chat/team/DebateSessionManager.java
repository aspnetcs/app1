package com.webchat.platformapi.ai.chat.team;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webchat.platformapi.ai.chat.team.dto.DebateTurnSession;
import com.webchat.platformapi.ai.chat.team.dto.TeamConversationContext;
import com.webchat.platformapi.ai.chat.team.dto.TeamTurnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis-only persistence manager for team debate sessions.
 * <p>
 * Key layout:
 * <pre>
 *   team:conversation:{conversationId}  -> TeamConversationContext JSON (long TTL)
 *   team:turn:{turnId}                  -> DebateTurnSession JSON (short TTL)
 * </pre>
 * <p>
 * All reads and writes enforce userId ownership validation.
 * In-memory storage is forbidden; this class is the only persistence entry point.
 */
@Service
public class DebateSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DebateSessionManager.class);
    private static final String CONV_PREFIX = "team:conversation:";
    private static final String TURN_PREFIX = "team:turn:";
    private static final String EVENT_PREFIX = "team:events:";
    private static final String LOCK_PREFIX = "team:conversation-lock:";

    private final StringRedisTemplate redis;
    private final TeamChatRuntimeConfigService runtimeConfigService;
    private final ObjectMapper objectMapper;

    public DebateSessionManager(
            StringRedisTemplate redis,
            TeamChatRuntimeConfigService runtimeConfigService
    ) {
        this.redis = redis;
        this.runtimeConfigService = runtimeConfigService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // ========== TeamConversationContext ==========

    /**
     * Save or update a team conversation context. Refreshes TTL on every write.
     */
    public void saveConversation(TeamConversationContext ctx) {
        ctx.setLastActiveAt(Instant.now());
        String json = serialize(ctx);
        redis.opsForValue().set(CONV_PREFIX + ctx.getConversationId(), json, conversationTtl());
    }

    /**
     * Load a conversation context with ownership validation.
     *
     * @throws SecurityException if the stored userId does not match
     * @throws IllegalStateException if the conversation has expired or does not exist
     */
    public TeamConversationContext loadConversation(String conversationId, String userId) {
        String json = redis.opsForValue().get(CONV_PREFIX + conversationId);
        if (json == null || json.isBlank()) {
            throw new IllegalStateException(
                    "Team conversation not found or expired: " + conversationId);
        }
        TeamConversationContext ctx = deserialize(json, TeamConversationContext.class);
        if (!userId.equals(ctx.getUserId())) {
            throw new SecurityException(
                    "User '" + userId + "' does not own conversation '" + conversationId + "'");
        }
        return ctx;
    }

    /**
     * Check if a conversation exists (without ownership check).
     */
    public boolean conversationExists(String conversationId) {
        return Boolean.TRUE.equals(redis.hasKey(CONV_PREFIX + conversationId));
    }

    // ========== DebateTurnSession ==========

    /**
     * Save or update a debate turn session. Uses short TTL.
     */
    public void saveTurn(DebateTurnSession turn) {
        String json = serialize(turn);
        redis.opsForValue().set(TURN_PREFIX + turn.getTurnId(), json, turnTtl());
    }

    /**
     * Load a turn session with ownership validation.
     *
     * @throws SecurityException if the stored userId does not match
     * @throws IllegalStateException if the turn has expired or does not exist
     */
    public DebateTurnSession loadTurn(String turnId, String userId) {
        String json = redis.opsForValue().get(TURN_PREFIX + turnId);
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Debate turn not found or expired: " + turnId);
        }
        DebateTurnSession turn = deserialize(json, DebateTurnSession.class);
        if (!userId.equals(turn.getUserId())) {
            throw new SecurityException(
                    "User '" + userId + "' does not own turn '" + turnId + "'");
        }
        return turn;
    }

    /**
     * Refresh TTL on conversation context (e.g., when a follow-up question arrives).
     */
    public void refreshConversationTtl(String conversationId) {
        redis.expire(CONV_PREFIX + conversationId, conversationTtl());
    }

    public boolean tryAcquireConversationLock(String conversationId, String token, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                LOCK_PREFIX + conversationId,
                token,
                ttl
        ));
    }

    public void releaseConversationLock(String conversationId, String token) {
        String key = LOCK_PREFIX + conversationId;
        String current = redis.opsForValue().get(key);
        if (token != null && token.equals(current)) {
            redis.delete(key);
        }
    }

    public void appendTurnEvent(String turnId, String event, Map<String, Object> data) {
        TeamTurnEvent stored = new TeamTurnEvent();
        stored.setEvent(event);
        stored.setData(data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data));
        stored.setTimestamp(Instant.now());

        String key = EVENT_PREFIX + turnId;
        redis.opsForList().rightPush(key, serialize(stored));
        redis.expire(key, turnTtl());
    }

    public TurnEventPage readTurnEvents(String turnId, String userId, long cursor, int limit) {
        DebateTurnSession turn = loadTurn(turnId, userId);
        long safeCursor = Math.max(0, cursor);
        int safeLimit = Math.max(1, limit);
        String key = EVENT_PREFIX + turnId;
        List<String> raw = redis.opsForList().range(key, safeCursor, safeCursor + safeLimit - 1);
        List<TeamTurnEvent> events = new ArrayList<>();
        if (raw != null) {
            for (int i = 0; i < raw.size(); i++) {
                TeamTurnEvent event = deserialize(raw.get(i), TeamTurnEvent.class);
                event.setCursor(safeCursor + i);
                events.add(event);
            }
        }
        long nextCursor = safeCursor + events.size();
        boolean completed = turn.getStage() == com.webchat.platformapi.ai.chat.team.dto.DebateStage.COMPLETED
                || turn.getStage() == com.webchat.platformapi.ai.chat.team.dto.DebateStage.FAILED;
        return new TurnEventPage(events, nextCursor, completed, turn.getStage().name());
    }

    // ========== Serialization ==========

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize team session object", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize team session JSON", e);
        }
    }

    private Duration conversationTtl() {
        return Duration.ofHours(runtimeConfigService.snapshot().conversationTtlHours());
    }

    private Duration turnTtl() {
        return Duration.ofMinutes(runtimeConfigService.snapshot().turnTtlMinutes());
    }

    public record TurnEventPage(List<TeamTurnEvent> events, long nextCursor, boolean completed, String stage) {
    }
}
