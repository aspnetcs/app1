package com.webchat.platformapi.ai.chat;

import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Stable chat-stream entry contract used by controllers and tests.
 * Keeps controller wiring independent from the heavy AiChatService implementation details.
 */
public interface ChatStreamStarter {

    boolean startStream(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String ip, String userAgent);

    boolean startStreams(UUID userId, List<ChatStreamTask> tasks, String ip, String userAgent);
}
