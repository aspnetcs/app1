package com.webchat.platformapi.ai.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageBlockEnvelopeFactoryTest {

    @Test
    void deltaPayloadKeepsAskIdAbortKeyAndMessageId() {
        Map<String, Object> payload = ChatMessageBlockEnvelopeFactory.deltaPayload(
                "req-1",
                "trace-1",
                Map.of(
                        "askId", "ask-1",
                        "abortKey", "abort-1",
                        "messageId", "msg-1"
                ),
                "hello"
        );

        assertEquals("req-1", payload.get("requestId"));
        assertEquals("trace-1", payload.get("traceId"));
        assertEquals("ask-1", payload.get("askId"));
        assertEquals("abort-1", payload.get("abortKey"));
        assertEquals("msg-1", payload.get("messageId"));
        Object blocks = payload.get("blocks");
        assertInstanceOf(List.class, blocks);
        assertTrue(((List<?>) blocks).size() == 1);
    }

    @Test
    void errorPayloadFallsBackAskIdAndAbortKeyToRequestId() {
        Map<String, Object> payload = ChatMessageBlockEnvelopeFactory.errorPayload(
                "req-2",
                "trace-2",
                null,
                "boom"
        );

        assertEquals("req-2", payload.get("askId"));
        assertEquals("req-2", payload.get("abortKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> block = (Map<String, Object>) ((List<?>) payload.get("blocks")).get(0);
        assertEquals("error", block.get("type"));
    }
}
