package com.webchat.platformapi.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.ChatStreamContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class WsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WsHandler.class);

    private static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final WsSessionRegistry registry;
    private final ChatStreamContextRegistry streamContextRegistry;

    public WsHandler(StringRedisTemplate redis, ObjectMapper objectMapper, WsSessionRegistry registry, ChatStreamContextRegistry streamContextRegistry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.streamContextRegistry = streamContextRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String ticket = queryParam(session.getUri(), "ticket");
        if (ticket == null || ticket.isBlank()) {
            session.close(UNAUTHORIZED);
            return;
        }

        String key = "ws_ticket:" + ticket;
        String json = redis.opsForValue().get(key);
        if (json != null) redis.delete(key);
        if (json == null || json.isBlank()) {
            session.close(UNAUTHORIZED);
            return;
        }

        Map<String, Object> data;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            data = map;
        } catch (Exception e) {
            session.close(UNAUTHORIZED);
            return;
        }

        String userIdStr = data.get("user_id") == null ? null : String.valueOf(data.get("user_id"));
        if (userIdStr == null || userIdStr.isBlank()) {
            session.close(UNAUTHORIZED);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (Exception e) {
            session.close(UNAUTHORIZED);
            return;
        }

        long now = System.currentTimeMillis();
        long expiresAt = data.get("expires_at") instanceof Number n ? n.longValue() : 0L;
        if (expiresAt > 0 && now > expiresAt) {
            session.close(UNAUTHORIZED);
            return;
        }

        session.getAttributes().put("userId", userId);
        String brand = data.get("brand") == null ? null : String.valueOf(data.get("brand")).trim();
        if (brand != null && !brand.isBlank()) session.getAttributes().put("brand", brand);
        String sid = data.get("session_id") == null ? null : String.valueOf(data.get("session_id")).trim();
        if (sid != null && !sid.isBlank()) session.getAttributes().put("sessionId", sid);

        registry.add(userId, session);

        // Minimal ACK to help clients confirm the connection is authenticated.
        send(session, "ws.ready", Map.of("userId", userId.toString()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj == null) return;
        try {
            UUID userId = userIdObj instanceof UUID u ? u : UUID.fromString(String.valueOf(userIdObj));
            registry.remove(userId, session);
        } catch (Exception e) {
            log.debug("[ws] remove session ignore: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Heartbeat: accept both plain "ping" and JSON {"type":"ping"} from clients.
        String payload = message.getPayload();
        if (payload == null) return;

        String trimmed = payload.trim();
        if (trimmed.equalsIgnoreCase("ping")) {
            send(session, "pong", Map.of());
            return;
        }

        if (!trimmed.startsWith("{")) return;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(trimmed, Map.class);
            Object typeObj = data == null ? null : data.get("type");
            String type = typeObj == null ? null : String.valueOf(typeObj).trim();
            if ("ping".equalsIgnoreCase(type)) {
                send(session, "pong", Map.of());
                return;
            }
            if ("chat.abort".equalsIgnoreCase(type)) {
                handleChatAbort(session, data);
                return;
            }
        } catch (Exception e) {
            log.debug("[ws] parse client msg failed: {}", e.getMessage());
        }
    }

    private void handleChatAbort(WebSocketSession session, Map<String, Object> payload) {
        UUID userId = safeUserId(session);
        if (userId == null) {
            return;
        }
        Object dataObj = payload == null ? null : payload.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            return;
        }
        String requestId = dataMap.get("requestId") == null ? "" : String.valueOf(dataMap.get("requestId")).trim();
        if (requestId.isEmpty()) {
            return;
        }
        String reason = dataMap.get("reason") == null ? "" : String.valueOf(dataMap.get("reason")).trim();

        streamContextRegistry.markAborted(userId, requestId, reason);

        // Send one terminal message for the client immediately; the stream loop will observe the abort flag and stop.
        boolean sendTerminal = streamContextRegistry.markTerminalSentIfAbsent(userId, requestId);
        if (sendTerminal) {
            String traceId = "";
            try {
                ChatStreamContextRegistry.Snapshot snapshot = streamContextRegistry.get(userId, requestId);
                if (snapshot != null && snapshot.traceId() != null && !snapshot.traceId().isBlank()) {
                    traceId = snapshot.traceId();
                }
            } catch (Exception ignored) {
                // best-effort only
            }
            registry.sendToUser(userId, "chat.error", Map.of(
                    "requestId", requestId,
                    "traceId", traceId,
                    "message", "aborted",
                    "reason", reason
            ));
        }
    }

    private static UUID safeUserId(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj == null) {
            return null;
        }
        try {
            return userIdObj instanceof UUID u ? u : UUID.fromString(String.valueOf(userIdObj));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void send(WebSocketSession session, String type, Object data) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("[ws] send failed sid={}", session.getId(), e);
        }
    }

    private static String queryParam(URI uri, String key) {
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null || query.isBlank()) return null;
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx < 0) continue;
            String k = part.substring(0, idx);
            if (!k.equals(key)) continue;
            String raw = part.substring(idx + 1);
            try {
                return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.debug("[ws] url decode fallback: {}", e.getMessage());
                return raw;
            }
        }
        return null;
    }
}
