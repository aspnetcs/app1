package com.webchat.platformapi.ai.chat;

import org.slf4j.Logger;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class SseEmitterFactory {

    private SseEmitterFactory() {
    }

    static SseEmitter create(long timeoutMs) {
        return new SseEmitter(timeoutMs);
    }

    static SseEmitter errorEmitter(Logger logger, String message) {
        SseEmitter emitter = create(5000L);
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        String traceId = UUID.randomUUID().toString().replace("-", "");
        try {
            emitter.send(SseEmitter.event().name("chat.error").data(normalizeData("chat.error", Map.of(
                    "requestId", requestId,
                    "traceId", traceId,
                    "message", message
            ))));
        } catch (Exception e) {
            logger.warn("Failed to send chat SSE error event: {}", message, e);
        }
        emitter.complete();
        return emitter;
    }

    static boolean safeSend(Logger logger, SseEmitter emitter, String event, Object data) {
        if (emitter == null) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(normalizeData(event, data)));
            return true;
        } catch (Exception e) {
            logger.warn("[ai.sse] emitter send failed: requestId={}, event={}, payloadType={}, error={}",
                    extractRequestId(data), event, payloadType(data), e.toString(), e);
            return false;
        }
    }

    static void sendAndComplete(Logger logger, SseEmitter emitter, String event, Object data) {
        safeSend(logger, emitter, event, data);
        if (emitter != null) {
            emitter.complete();
        }
    }

    private static String extractRequestId(Object data) {
        if (data instanceof Map<?, ?> payload) {
            Object requestId = payload.get("requestId");
            if (requestId != null) {
                return String.valueOf(requestId);
            }
        }
        return "";
    }

    private static String payloadType(Object data) {
        return data == null ? "null" : data.getClass().getSimpleName();
    }

    private static Object normalizeData(String event, Object data) {
        if (event == null || !event.startsWith("chat.")) {
            return data;
        }
        if (!(data instanceof Map<?, ?> raw)) {
            return data;
        }
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
        // Required v1 presence; values may be empty if the caller has no context.
        out.putIfAbsent("requestId", "");
        out.putIfAbsent("traceId", "");
        return out;
    }
}
