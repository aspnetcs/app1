package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatSseEventServiceTest {

    @Test
    void finishSuccessfulStreamIncludesTraceIdInDonePayload() {
        ChannelMonitor channelMonitor = mock(ChannelMonitor.class);
        AuditService auditService = mock(AuditService.class);
        ChatSseEventService service = new ChatSseEventService(channelMonitor, auditService);

        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AtomicReference<Object> sentPayload = new AtomicReference<>();
        TrackingEmitter emitter = new TrackingEmitter();

        service.finishSuccessfulStream(
                userId,
                "req-sse",
                "trace-sse",
                "gpt-5.4-mini",
                11L,
                "openai",
                22L,
                Map.of(
                        "askId", "ask-sse",
                        "abortKey", "abort-sse"
                ),
                emitter,
                Instant.parse("2026-04-18T00:00:00Z"),
                "127.0.0.1",
                "JUnit",
                5,
                7,
                12,
                (target, event, data) -> {
                    assertEquals("chat.done", event);
                    sentPayload.set(data);
                    return true;
                }
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) sentPayload.get();
        assertNotNull(payload);
        assertEquals("req-sse", payload.get("requestId"));
        assertEquals("trace-sse", payload.get("traceId"));
        assertEquals("ask-sse", payload.get("askId"));
        assertEquals("abort-sse", payload.get("abortKey"));
        assertTrue(payload.containsKey("blocks"));
        assertTrue(emitter.completed);
        verify(channelMonitor).recordSuccess(11L, 22L);
    }

    private static final class TrackingEmitter extends SseEmitter {
        private boolean completed;

        @Override
        public synchronized void complete() {
            completed = true;
            super.complete();
        }
    }
}
