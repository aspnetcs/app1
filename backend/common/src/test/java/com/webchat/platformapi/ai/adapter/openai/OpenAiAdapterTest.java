package com.webchat.platformapi.ai.adapter.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenAiAdapterTest {

    @Test
    void buildChatRequestAddsBearerPrefixForRawToken() throws Exception {
        OpenAiAdapter adapter = new OpenAiAdapter(new ObjectMapper());
        AiChannelEntity channel = new AiChannelEntity();
        channel.setBaseUrl("https://example.com/v1");

        HttpRequest req = adapter.buildChatRequest(
                Map.of(
                        "model", "gpt-5",
                        "messages", List.of(Map.of("role", "user", "content", "ping"))
                ),
                channel,
                "sk-test",
                false
        );

        assertEquals("Bearer sk-test", req.headers().firstValue("Authorization").orElse(null));
        assertEquals("https://example.com/v1/chat/completions", req.uri().toString());
    }

    @Test
    void buildChatRequestDoesNotDoublePrefixBearer() throws Exception {
        OpenAiAdapter adapter = new OpenAiAdapter(new ObjectMapper());
        AiChannelEntity channel = new AiChannelEntity();
        channel.setBaseUrl("https://example.com/v1");

        HttpRequest req = adapter.buildChatRequest(
                Map.of(
                        "model", "gpt-5",
                        "messages", List.of(Map.of("role", "user", "content", "ping"))
                ),
                channel,
                "Bearer sk-test",
                false
        );

        assertEquals("Bearer sk-test", req.headers().firstValue("Authorization").orElse(null));
        assertEquals("https://example.com/v1/chat/completions", req.uri().toString());
    }

    @Test
    void buildChatRequestOmitsAuthorizationHeaderWhenBlank() throws Exception {
        OpenAiAdapter adapter = new OpenAiAdapter(new ObjectMapper());
        AiChannelEntity channel = new AiChannelEntity();
        channel.setBaseUrl("https://example.com/v1");

        HttpRequest req = adapter.buildChatRequest(
                Map.of(
                        "model", "gpt-5",
                        "messages", List.of(Map.of("role", "user", "content", "ping"))
                ),
                channel,
                "   ",
                false
        );

        assertFalse(req.headers().firstValue("Authorization").isPresent());
        assertEquals("https://example.com/v1/chat/completions", req.uri().toString());
    }

    @Test
    void buildChatRequestRespectsV2ApiPrefix() throws Exception {
        OpenAiAdapter adapter = new OpenAiAdapter(new ObjectMapper());
        AiChannelEntity channel = new AiChannelEntity();
        channel.setBaseUrl("https://example.com/v2");

        HttpRequest req = adapter.buildChatRequest(
                Map.of(
                        "model", "xop3qwen1b7",
                        "messages", List.of(Map.of("role", "user", "content", "ping"))
                ),
                channel,
                "sk-test",
                false
        );

        assertEquals("https://example.com/v2/chat/completions", req.uri().toString());
    }

    @Test
    void buildChatRequestDefaultsToV1WhenNoVersionProvided() throws Exception {
        OpenAiAdapter adapter = new OpenAiAdapter(new ObjectMapper());
        AiChannelEntity channel = new AiChannelEntity();
        channel.setBaseUrl("https://example.com");

        HttpRequest req = adapter.buildChatRequest(
                Map.of(
                        "model", "gpt-5.4-mini",
                        "messages", List.of(Map.of("role", "user", "content", "ping"))
                ),
                channel,
                "sk-test",
                false
        );

        assertEquals("https://example.com/v1/chat/completions", req.uri().toString());
    }
}
