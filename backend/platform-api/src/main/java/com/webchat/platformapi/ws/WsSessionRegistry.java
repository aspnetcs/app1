package com.webchat.platformapi.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.ChatStreamContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WsSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsSessionRegistry.class);
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ChatStreamContextRegistry streamContext;

    public WsSessionRegistry(ObjectMapper objectMapper, @Nullable ChatStreamContextRegistry streamContext) {
        this.objectMapper = objectMapper;
        this.streamContext = streamContext;
    }

    public void add(UUID userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        sessionsByUser.compute(userId, (ignored, map) -> {
            if (map == null) map = new ConcurrentHashMap<>();
            map.put(session.getId(), session);
            return map;
        });
    }

    public void remove(UUID userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        String sessionId = session.getId();
        if (sessionId == null) return;
        sessionsByUser.computeIfPresent(userId, (ignored, map) -> {
            map.remove(sessionId);
            return map.isEmpty() ? null : map;
        });
    }

    public int sendToUser(UUID userId, String type, Object data) {
        Object payloadData;
        String json;
        try {
            payloadData = normalizeData(userId, type, data);
            markTerminalIfNeeded(userId, type, payloadData);
            json = objectMapper.writeValueAsString(Map.of("type", type, "data", payloadData));
        } catch (Exception e) {
            return 0;
        }

        ConcurrentHashMap<String, WebSocketSession> map = sessionsByUser.get(userId);
        if (map == null || map.isEmpty()) return 0;
        TextMessage msg = new TextMessage(json);

        int sent = 0;
        for (WebSocketSession s : map.values()) {
            if (s == null || !s.isOpen()) continue;
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
                sent += 1;
            } catch (Exception e) {
                log.warn("[ws] sendToUser failed sid={}", s.getId(), e);
            }
        }
        return sent;
    }

    private Object normalizeData(UUID userId, String type, Object data) {
        if (type == null || !type.startsWith("chat.")) {
            return data;
        }
        if (!(data instanceof Map<?, ?> raw)) {
            return data;
        }
        // Backward compatible: keep existing keys, only add v1 fields if absent.
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            Object k = e.getKey();
            if (k == null) {
                continue;
            }
            out.put(String.valueOf(k), e.getValue());
        }

        out.putIfAbsent("v", 1);
        out.putIfAbsent("ts", System.currentTimeMillis());

        String requestId = out.get("requestId") == null ? "" : String.valueOf(out.get("requestId")).trim();
        String traceId = out.get("traceId") == null ? "" : String.valueOf(out.get("traceId")).trim();

        ChatStreamContextRegistry.Snapshot snapshot = null;
        if (streamContext != null && !requestId.isEmpty() && userId != null) {
            snapshot = streamContext.get(userId, requestId);
        }
        // v1 contract requires presence; keep additive behavior by filling empty values if missing.
        if (!out.containsKey("requestId")) {
            out.put("requestId", requestId);
        }

        if (traceId.isEmpty() && snapshot != null && snapshot.traceId() != null && !snapshot.traceId().isBlank()) {
            traceId = snapshot.traceId();
            out.put("traceId", traceId);
        }
        if (!out.containsKey("traceId")) {
            out.put("traceId", traceId);
        }
        if (snapshot != null) {
            if (!out.containsKey("model") && snapshot.model() != null && !snapshot.model().isBlank()) {
                out.put("model", snapshot.model());
            }
            if (!out.containsKey("channelId") && snapshot.channelId() != null && !snapshot.channelId().isBlank()) {
                out.put("channelId", snapshot.channelId());
            }
            if (!out.containsKey("channelType") && snapshot.channelType() != null && !snapshot.channelType().isBlank()) {
                out.put("channelType", snapshot.channelType());
            }
            if (!out.containsKey("roundId") && snapshot.roundId() != null && !snapshot.roundId().isBlank()) {
                out.put("roundId", snapshot.roundId());
            }
        }
        return out;
    }

    private void markTerminalIfNeeded(UUID userId, String type, Object data) {
        if (streamContext == null || userId == null) {
            return;
        }
        if (!"chat.done".equals(type) && !"chat.error".equals(type)) {
            return;
        }
        if (!(data instanceof Map<?, ?> payload)) {
            return;
        }
        Object requestIdObj = payload.get("requestId");
        if (requestIdObj == null) {
            return;
        }
        String requestId = String.valueOf(requestIdObj).trim();
        if (requestId.isEmpty()) {
            return;
        }
        streamContext.markTerminalSentIfAbsent(userId, requestId);
    }
}
