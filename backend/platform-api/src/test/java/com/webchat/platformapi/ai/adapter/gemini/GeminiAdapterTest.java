package com.webchat.platformapi.ai.adapter.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiAdapterTest {

    private final GeminiAdapter adapter = new GeminiAdapter(new ObjectMapper());

    @Test
    void parsesTextDelta() throws Exception {
        StreamChunk c = adapter.parseStreamLine("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}]}}]}");
        assertNotNull(c);
        assertEquals("Hello", c.delta());
        assertFalse(c.done());
        assertNull(c.errorMessage());
    }

    @Test
    void parsesDoneWithFinishReason() throws Exception {
        StreamChunk c = adapter.parseStreamLine("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Bye\"}]},\"finishReason\":\"STOP\"}]}");
        assertNotNull(c);
        assertEquals("Bye", c.delta());
        assertTrue(c.done());
    }

    @Test
    void supportsDataPrefix() throws Exception {
        StreamChunk c = adapter.parseStreamLine("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}");
        assertNotNull(c);
        assertEquals("Hi", c.delta());
    }
}

