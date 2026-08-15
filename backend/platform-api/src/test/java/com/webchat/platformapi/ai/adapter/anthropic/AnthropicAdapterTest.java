package com.webchat.platformapi.ai.adapter.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicAdapterTest {

    private final AnthropicAdapter adapter = new AnthropicAdapter(new ObjectMapper());

    @Test
    void parsesTextDelta() throws Exception {
        StreamChunk c = adapter.parseStreamLine("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");
        assertNotNull(c);
        assertEquals("Hi", c.delta());
        assertFalse(c.done());
        assertNull(c.errorMessage());
    }

    @Test
    void parsesDone() throws Exception {
        StreamChunk c = adapter.parseStreamLine("data: {\"type\":\"message_stop\"}");
        assertNotNull(c);
        assertTrue(c.done());
    }

    @Test
    void parsesError() throws Exception {
        StreamChunk c = adapter.parseStreamLine("data: {\"type\":\"error\",\"error\":{\"message\":\"bad\"}}");
        assertNotNull(c);
        assertTrue(c.done());
        assertEquals("bad", c.errorMessage());
    }
}

