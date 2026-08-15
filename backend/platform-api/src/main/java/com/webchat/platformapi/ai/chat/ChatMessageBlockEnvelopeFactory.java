package com.webchat.platformapi.ai.chat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChatMessageBlockEnvelopeFactory {

    private ChatMessageBlockEnvelopeFactory() {
    }

    static Map<String, Object> deltaPayload(String requestId,
                                            String traceId,
                                            Map<String, Object> requestBody,
                                            String delta) {
        LinkedHashMap<String, Object> payload = basePayload(requestId, traceId, requestBody);
        payload.put("delta", delta);
        payload.put("done", false);
        attachBlock(payload, "text", "text-0", "streaming", Map.of("text", delta == null ? "" : delta));
        return payload;
    }

    static Map<String, Object> thinkingPayload(String requestId,
                                               String traceId,
                                               Map<String, Object> requestBody,
                                               String delta) {
        LinkedHashMap<String, Object> payload = basePayload(requestId, traceId, requestBody);
        payload.put("delta", delta);
        payload.put("done", false);
        attachBlock(payload, "text", "thinking-0", "streaming", Map.of("text", delta == null ? "" : delta));
        return payload;
    }

    static Map<String, Object> donePayload(String requestId,
                                           String traceId,
                                           Map<String, Object> requestBody) {
        LinkedHashMap<String, Object> payload = basePayload(requestId, traceId, requestBody);
        payload.put("done", true);
        attachBlock(payload, "text", "text-0", "final", Map.of());
        return payload;
    }

    static Map<String, Object> errorPayload(String requestId,
                                            String traceId,
                                            Map<String, Object> requestBody,
                                            String message) {
        LinkedHashMap<String, Object> payload = basePayload(requestId, traceId, requestBody);
        payload.put("message", message);
        payload.put("error", message);
        payload.put("done", true);
        attachBlock(payload, "error", "error-0", "final", Map.of("message", message == null ? "" : message));
        return payload;
    }

    static Map<String, Object> noticePayload(String requestId,
                                             String traceId,
                                             Map<String, Object> requestBody,
                                             String message) {
        LinkedHashMap<String, Object> payload = basePayload(requestId, traceId, requestBody);
        payload.put("message", message);
        return payload;
    }

    static String resolveAskId(Map<String, Object> requestBody, String requestId) {
        return firstString(requestBody, requestId, "askId", "ask_id", "roundId", "round_id", "conversationRoundId", "conversation_round_id");
    }

    static String resolveAbortKey(Map<String, Object> requestBody, String requestId) {
        return firstString(requestBody, requestId, "abortKey", "abort_key");
    }

    private static LinkedHashMap<String, Object> basePayload(String requestId,
                                                             String traceId,
                                                             Map<String, Object> requestBody) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId == null ? "" : requestId);
        payload.put("traceId", traceId == null ? "" : traceId);
        payload.put("askId", resolveAskId(requestBody, requestId));
        payload.put("abortKey", resolveAbortKey(requestBody, requestId));
        String messageId = firstString(requestBody, null, "messageId", "message_id");
        if (messageId != null) {
            payload.put("messageId", messageId);
        }
        return payload;
    }

    private static void attachBlock(LinkedHashMap<String, Object> payload,
                                    String type,
                                    String key,
                                    String status,
                                    Map<String, Object> blockPayload) {
        LinkedHashMap<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.put("key", key);
        block.put("sequence", 0);
        block.put("status", status);
        block.put("payload", blockPayload);
        payload.put("block", block);
        payload.put("blocks", List.of(block));
    }

    private static String firstString(Map<String, Object> requestBody, String fallbackValue, String... keys) {
        if (keys == null || keys.length == 0) {
            return fallbackValue;
        }
        if (requestBody != null) {
            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                Object value = requestBody.get(key);
                if (value == null) {
                    continue;
                }
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return fallbackValue;
    }
}
