package com.webchat.platformapi.ai.adapter.anthropic;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAdapter.class);

    private static final String DEFAULT_VERSION = "2023-06-01";

    private final ObjectMapper objectMapper;

    public AnthropicAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "anthropic";
    }

    @Override
    public HttpRequest buildChatRequest(Map<String, Object> requestBody, AiChannelEntity channel, String apiKey, boolean stream) throws Exception {
        String baseUrl = channel == null ? "" : channel.getBaseUrl();
        baseUrl = normalizeBaseUrl(baseUrl);
        String url = UrlUtil.join(baseUrl, "/v1/messages");

        Map<String, Object> body = toAnthropicBody(requestBody, stream);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(stream ? Duration.ofMinutes(10) : Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (stream) b.header("Accept", "text/event-stream");
        else b.header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) b.header("x-api-key", apiKey.trim());

        String version = extraString(channel, "anthropic_version", "anthropicVersion");
        if (version == null || version.isBlank()) version = DEFAULT_VERSION;
        b.header("anthropic-version", version.trim());

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
        if ("[DONE]".equalsIgnoreCase(data)) return StreamChunk.doneChunk();

        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (Exception e) {
            log.debug("[anthropic] parse data failed: {}", line);
            return null;
        }

        JsonNode err = node.get("error");
        if (err != null && !err.isNull()) {
            String msg = err.has("message") ? err.get("message").asText() : err.toString();
            return StreamChunk.error(msg);
        }

        String type = node.path("type").asText("");

        // Extract usage from message_start (input_tokens) and message_delta (output_tokens)
        if ("message_start".equals(type)) {
            JsonNode usage = node.path("message").path("usage");
            if (!usage.isMissingNode() && usage.has("input_tokens")) {
                Integer inputTokens = usage.get("input_tokens").asInt();
                return StreamChunk.withUsage(inputTokens, null, null);
            }
            return null;
        }
        if ("message_delta".equals(type)) {
            JsonNode usage = node.get("usage");
            if (usage != null && !usage.isNull() && usage.has("output_tokens")) {
                Integer outputTokens = usage.get("output_tokens").asInt();
                return StreamChunk.withUsage(null, outputTokens, null);
            }
            return null;
        }
        if ("message_stop".equals(type)) return StreamChunk.doneChunk();

        if ("content_block_start".equals(type)) {
            String text = node.path("content_block").path("text").asText("");
            if (text != null && !text.isEmpty()) return StreamChunk.delta(text);
            return null;
        }

        if (!"content_block_delta".equals(type)) return null;

        String deltaType = node.path("delta").path("type").asText("");
        if (!deltaType.isEmpty() && !"text_delta".equals(deltaType)) return null;

        String text = node.path("delta").path("text").asText("");
        if (text == null || text.isEmpty()) return null;
        return StreamChunk.delta(text);
    }

    private static Map<String, Object> toAnthropicBody(Map<String, Object> openAiBody, boolean stream) {
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> in = openAiBody == null ? Map.of() : openAiBody;

        String model = string(in.get("model"));
        if (!model.isEmpty()) out.put("model", model);

        Integer maxTokens = asInt(in.get("max_tokens"));
        if (maxTokens == null) maxTokens = asInt(in.get("max_completion_tokens"));
        if (maxTokens == null || maxTokens <= 0) maxTokens = 1024;
        out.put("max_tokens", maxTokens);

        out.put("stream", stream);

        Double temperature = asDouble(in.get("temperature"));
        if (temperature != null) out.put("temperature", temperature);
        Double topP = asDouble(in.get("top_p"));
        if (topP != null) out.put("top_p", topP);
        Integer topK = asInt(in.get("top_k"));
        if (topK != null) out.put("top_k", topK);

        List<String> systemParts = new ArrayList<>();
        List<Map<String, Object>> messages = toAnthropicMessages(in.get("messages"), systemParts);
        if (!systemParts.isEmpty()) out.put("system", String.join("\n\n", systemParts));
        out.put("messages", messages);

        List<String> stops = toStringList(in.get("stop"));
        if (!stops.isEmpty()) out.put("stop_sequences", stops);

        return out;
    }

    private static List<Map<String, Object>> toAnthropicMessages(Object messages, List<String> systemParts) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(messages instanceof Iterable<?> list)) return out;

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String role = string(map.get("role")).toLowerCase();

            if ("system".equals(role)) {
                String text = extractText(map.get("content"));
                if (!text.isEmpty()) systemParts.add(text);
                continue;
            }

            if (!"user".equals(role) && !"assistant".equals(role)) role = "user";

            Object contentObj = map.get("content");
            // Handle multimodal content array (text + image_url parts)
            if (contentObj instanceof Iterable<?> contentList && hasImageParts(contentList)) {
                List<Map<String, Object>> anthropicContent = convertToAnthropicContent(contentList);
                if (!anthropicContent.isEmpty()) {
                    out.add(Map.of("role", role, "content", anthropicContent));
                }
                continue;
            }

            String content = extractText(contentObj);
            if (content.isEmpty()) continue;

            out.add(Map.of("role", role, "content", content));
        }

        return out;
    }

    private static boolean hasImageParts(Iterable<?> parts) {
        for (Object part : parts) {
            if (part instanceof Map<?, ?> map) {
                String type = string(map.get("type"));
                if ("image_url".equals(type)) return true;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> convertToAnthropicContent(Iterable<?> parts) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object part : parts) {
            if (!(part instanceof Map<?, ?> map)) continue;
            String type = string(map.get("type"));
            if ("text".equals(type)) {
                String text = string(map.get("text"));
                if (!text.isEmpty()) {
                    out.add(Map.of("type", "text", "text", text));
                }
            } else if ("image_url".equals(type)) {
                Object imageUrlObj = map.get("image_url");
                if (imageUrlObj instanceof Map<?, ?> urlMap) {
                    String url = string(urlMap.get("url"));
                    if (!url.isEmpty()) {
                        out.add(Map.of(
                                "type", "image",
                                "source", Map.of("type", "url", "url", url)
                        ));
                    }
                }
            }
        }
        return out;
    }

    private static String extractText(Object content) {
        if (content == null) return "";
        if (content instanceof String s) return s.trim();

        if (content instanceof Iterable<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object part : list) {
                if (part instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    Object text = map.get("text");
                    if ("text".equals(String.valueOf(type)) && text != null) {
                        if (!sb.isEmpty()) sb.append('\n');
                        sb.append(String.valueOf(text));
                        continue;
                    }
                }
                if (part != null) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(String.valueOf(part));
                }
            }
            return sb.toString().trim();
        }

        return String.valueOf(content).trim();
    }

    private static List<String> toStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof String s) {
            String t = s.trim();
            return t.isEmpty() ? List.of() : List.of(t);
        }
        if (v instanceof Iterable<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String t = string(item);
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }
        String t = string(v);
        return t.isEmpty() ? List.of() : List.of(t);
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            String t = String.valueOf(v).trim();
            if (t.isEmpty()) return null;
            return Integer.parseInt(t);
        } catch (Exception e) {
            log.debug("[anthropic] asInt parse failed: {}", v);
            return null;
        }
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            String t = String.valueOf(v).trim();
            if (t.isEmpty()) return null;
            return Double.parseDouble(t);
        } catch (Exception e) {
            log.debug("[anthropic] asDouble parse failed: {}", v);
            return null;
        }
    }

    private static String string(Object v) {
        String t = v == null ? "" : String.valueOf(v).trim();
        return t == null ? "" : t;
    }

    private static String extraString(AiChannelEntity channel, String... keys) {
        if (channel == null) return null;
        Map<String, Object> extra = channel.getExtraConfig();
        if (extra == null || extra.isEmpty() || keys == null) return null;
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            Object v = extra.get(k);
            String t = v == null ? "" : String.valueOf(v).trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        String b = baseUrl.trim();
        if (b.endsWith("/v1/")) return b.substring(0, b.length() - 4);
        if (b.endsWith("/v1")) return b.substring(0, b.length() - 3);
        return b;
    }
}

