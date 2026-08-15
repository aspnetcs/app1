package com.webchat.platformapi.ai.chat;

import java.util.Map;

public record ChatStreamTask(String requestId, String traceId, Map<String, Object> requestBody) {
}
