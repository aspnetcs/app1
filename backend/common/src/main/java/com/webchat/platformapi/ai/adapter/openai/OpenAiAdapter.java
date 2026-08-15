package com.webchat.platformapi.ai.adapter.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import com.webchat.platformapi.ai.adapter.UrlUtil;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class OpenAiAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAdapter.class);

    private final ObjectMapper objectMapper;

    public OpenAiAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "openai";
    }

    @Override
    public HttpRequest buildChatRequest(Map<String, Object> requestBody, AiChannelEntity channel, String apiKey, boolean stream) throws Exception {
        String baseUrl = channel == null ? "" : channel.getBaseUrl();
        // Some OpenAI-compatible providers expose the API under /v2 (not /v1).
        // Treat baseUrl that already ends with /v1 or /v2 as an API prefix; otherwise default to /v1.
        String apiPrefix = normalizeApiPrefix(baseUrl);
        String url = UrlUtil.join(apiPrefix, "/chat/completions");

        String json = objectMapper.writeValueAsString(requestBody == null ? Map.of() : requestBody);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(stream ? Duration.ofMinutes(10) : Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (stream) b.header("Accept", "text/event-stream");
        else b.header("Accept", "application/json");
        String authorization = buildAuthorizationHeader(apiKey);
        if (authorization != null) b.header("Authorization", authorization);

        // Ensure stream_options.include_usage is set so usage data comes in the final chunk
        if (stream && requestBody != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mutable = requestBody instanceof java.util.HashMap ? requestBody : new java.util.HashMap<>(requestBody);
            Object so = mutable.get("stream_options");
            if (so == null) {
                mutable.put("stream_options", Map.of("include_usage", true));
                json = objectMapper.writeValueAsString(mutable);
            }
        }

        return b.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
    }

    @Override
    public StreamChunk parseStreamLine(String line) throws Exception {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;

        if (!trimmed.startsWith("data:")) return null;

        String data = trimmed.substring(5).trim();
        if (data.isEmpty()) return null;
        if ("[DONE]".equals(data)) return StreamChunk.doneChunk();

        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (Exception e) {
            log.debug("[openai] parse stream line failed: {}", line);
            return null;
        }

        JsonNode err = node.get("error");
        if (err != null && !err.isNull()) {
            String msg = err.has("message") ? err.get("message").asText() : err.toString();
            return StreamChunk.error(msg);
        }

        JsonNode deltaNode = node.path("choices").path(0).path("delta");
        JsonNode contentNode = deltaNode.path("content");
        // DeepSeek R1 / Qwen QwQ / reasoning models send thinking in "reasoning_content"
        JsonNode reasoningNode = deltaNode.path("reasoning_content");

        String text = null;
        if (!contentNode.isMissingNode() && !contentNode.isNull()) {
            text = contentNode.asText();
            if (text != null && text.isEmpty()) text = null;
        }

        String reasoning = null;
        if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
            reasoning = reasoningNode.asText();
            if (reasoning != null && reasoning.isEmpty()) reasoning = null;
        }

        if (text == null && reasoning == null) {
            // Check for usage in the final chunk (OpenAI sends usage just before [DONE])
            JsonNode usage = node.get("usage");
            if (usage != null && !usage.isNull()) {
                Integer pt = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
                Integer ct = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
                Integer tt = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : null;
                return StreamChunk.withUsage(pt, ct, tt);
            }
            return null;
        }

        if (text != null && reasoning != null) {
            return StreamChunk.deltaWithReasoning(text, reasoning);
        }
        if (reasoning != null) {
            return StreamChunk.reasoning(reasoning);
        }
        return StreamChunk.delta(text);
    }

    /**
     * Normalize the upstream base URL into an OpenAI API prefix.
     * <p>
     * - If {@code baseUrl} already ends with {@code /v1} or {@code /v2}, keep it.
     * - Otherwise default to {@code /v1}.
     */
    private static String normalizeApiPrefix(String baseUrl) {
        if (baseUrl == null) return "";
        String b = baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/v1") || b.endsWith("/v2")) return b;
        return b + "/v1";
    }

    /**
     * Normalize an API key into an HTTP Authorization header value.
     * Accepts either a raw token ("sk-...") or a full scheme value ("Bearer sk-...").
     */
    private static String buildAuthorizationHeader(String apiKey) {
        if (apiKey == null) return null;
        String token = apiKey.trim();
        if (token.isEmpty()) return null;
        // If the admin pasted a full Authorization value, don't double-prefix.
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) return token;
        if (token.regionMatches(true, 0, "Basic ", 0, 6)) return token;
        return "Bearer " + token;
    }
}
