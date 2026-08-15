package com.webchat.platformapi.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.ChatStreamContextRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsSessionRegistryTest {

    @Test
    void sendToUserAddsV1FieldsAndFillsTraceIdFromContext() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ChatStreamContextRegistry ctx = new ChatStreamContextRegistry();
        WsSessionRegistry registry = new WsSessionRegistry(mapper, ctx);

        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String requestId = "req_test";
        String traceId = "trace_test";
        ctx.registerStart(userId, requestId, traceId, "gpt-5.4-mini");

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        doNothing().when(session).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));

        registry.add(userId, session);
        registry.sendToUser(userId, "chat.delta", Map.of("requestId", requestId, "delta", "hi"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(captor.getValue().getPayload(), Map.class);
        assertEquals("chat.delta", payload.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        assertNotNull(data);
        assertEquals(1, ((Number) data.get("v")).intValue());
        assertTrue(((Number) data.get("ts")).longValue() > 0L);
        assertEquals(requestId, String.valueOf(data.get("requestId")));
        assertEquals(traceId, String.valueOf(data.get("traceId")));
    }

    @Test
    void sendToUserEnsuresTraceIdKeyPresenceEvenWithoutContext() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WsSessionRegistry registry = new WsSessionRegistry(mapper, null);

        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String requestId = "req_test";

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        doNothing().when(session).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));

        registry.add(userId, session);
        registry.sendToUser(userId, "chat.error", Map.of("requestId", requestId, "message", "aborted"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(captor.getValue().getPayload(), Map.class);
        assertEquals("chat.error", payload.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        assertNotNull(data);
        assertTrue(data.containsKey("traceId"));
        assertEquals("", String.valueOf(data.get("traceId")));
    }
}

