package com.webchat.platformapi.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.ChatStreamContextRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsHandlerTest {

    @Test
    void handleTextMessageWhenChatAbort_marksAbortedAndSendsTerminal() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        WsSessionRegistry registry = mock(WsSessionRegistry.class);
        ChatStreamContextRegistry ctx = mock(ChatStreamContextRegistry.class);
        ObjectMapper mapper = new ObjectMapper();

        WsHandler handler = new WsHandler(redis, mapper, registry, ctx);

        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String requestId = "req_test";

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        when(session.getAttributes()).thenReturn(attrs);

        when(ctx.markTerminalSentIfAbsent(userId, requestId)).thenReturn(true);
        when(ctx.get(userId, requestId)).thenReturn(new ChatStreamContextRegistry.Snapshot(
                "trace_test",
                "",
                "",
                "",
                "",
                true,
                "user_cancel",
                true
        ));
        when(registry.sendToUser(eq(userId), eq("chat.error"), any())).thenReturn(1);

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "type": "chat.abort",
                  "data": { "requestId": "req_test", "reason": "user_cancel" }
                }
                """));

        verify(ctx).markAborted(userId, requestId, "user_cancel");
        verify(registry).sendToUser(eq(userId), eq("chat.error"), eq(Map.of(
                "requestId", requestId,
                "traceId", "trace_test",
                "message", "aborted",
                "reason", "user_cancel"
        )));
    }

    @Test
    void handleTextMessageWhenRequestAlreadyFinished_doesNotSendDuplicateAbortTerminal() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        ChatStreamContextRegistry ctx = new ChatStreamContextRegistry();
        WsSessionRegistry registry = new WsSessionRegistry(mapper, ctx);
        WsHandler handler = new WsHandler(redis, mapper, registry, ctx);

        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String requestId = "req_done";

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        doNothing().when(session).sendMessage(any(TextMessage.class));

        ctx.registerStart(userId, requestId, "trace_done", "gpt-5.4-mini");
        registry.add(userId, session);
        registry.sendToUser(userId, "chat.done", Map.of(
                "requestId", requestId,
                "traceId", "trace_done"
        ));

        ChatStreamContextRegistry.Snapshot snapshot = ctx.get(userId, requestId);
        assertNotNull(snapshot);
        assertTrue(snapshot.terminalSent());
        verify(session, times(1)).sendMessage(any(TextMessage.class));

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "type": "chat.abort",
                  "data": { "requestId": "req_done", "reason": "user_cancel" }
                }
                """));

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
