package com.webchat.platformapi.ai.adapter.gemini;

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
public class GeminiAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(GeminiAdapter.class);

    private final ObjectMapper objectMapper;

    public GeminiAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "gemini";
    }

    @Override
    public HttpRequest buildChatRequest(Map<String, Object> requestBody, AiChannelEntity channel, String apiKey, boolean stream) throws Exception {
        String baseUrl = channel == null ? "" : channel.getBaseUrl();
        baseUrl = normalizeBaseUrl(baseUrl);

        Map<String, Object> body = toGeminiBody(requestBody);
        String model = string(requestBody == null ? null : requestBody.get("model"));
        String modelPath = normalizeModelPath(model);

        String op = stream ? "streamGenerateContent" : "generateContent";
        String url = UrlUtil.join(baseUrl, "/v1beta/" + modelPath + ":" + op);

        String json = objectMapper.writeValueAsString(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(stream ? Duration.ofMinutes(10) : Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) b.header("x-goog-api-key", apiKey.trim());

        return b.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
    }

    @Override
    public StreamChunk parseStreamLine(String line) throws Exception {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;

        String json = trimmed;
        if (trimmed.startsWith("data:")) {
            json = trimmed.substring(5).trim();
            if (json.isEmpty()) return null;
            if ("[DONE]".equalsIgnoreCase(json)) return StreamChunk.doneChunk();
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            log.debug("[gemini] parse stream line failed: {}", line);
            return null;
        }

        JsonNode err = node.get("error");
        if (err != null && !err.isNull()) {
            String msg = err.has("message") ? err.get("message").asText() : err.toString();
            return StreamChunk.error(msg);
        }

        JsonNode cand = node.path("candidates").path(0);
        if (cand.isMissingNode() || cand.isNull()) {
            // Check for usageMetadata even if candidates is missing
            JsonNode usage = node.get("usageMetadata");
            if (usage != null && !usage.isNull()) {
                Integer pt = usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
                Integer ct = usage.has("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : null;
                Integer tt = usage.has("totalTokenCount") ? usage.get("totalTokenCount").asInt() : null;
                return StreamChunk.withUsage(pt, ct, tt);
            }
            return null;
        }

        String finishReason = cand.path("finishReason").asText("");
        boolean done = finishReason != null && !finishReason.isBlank();

        String delta = extractCandidateText(cand);

        // Extract usageMetadata from final chunk (comes with finishReason)
        Integer pt = null, ct = null, tt = null;
        JsonNode usage = node.get("usageMetadata");
        if (usage != null && !usage.isNull()) {
            pt = usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
            ct = usage.has("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : null;
            tt = usage.has("totalTokenCount") ? usage.get("totalTokenCount").asInt() : null;
        }

        if ((delta == null || delta.isEmpty()) && done) {
            if (pt != null || ct != null || tt != null) return StreamChunk.withUsage(pt, ct, tt);
            return StreamChunk.doneChunk();
        }
        if (delta == null || delta.isEmpty()) return null;

        // If this is the final chunk with both delta and usage, return delta first (usage will come in done chunk)
        if (done && (pt != null || ct != null || tt != null)) {
            // Return as a delta+done chunk with usage
            return new StreamChunk(delta, null, true, null, pt, ct, tt);
        }
        return new StreamChunk(delta, null, done, null, null, null, null);
    }

    private static Map<String, Object> toGeminiBody(Map<String, Object> openAiBody) {
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> in = openAiBody == null ? Map.of() : openAiBody;

        List<String> systemParts = new ArrayList<>();
        List<Map<String, Object>> contents = toGeminiContents(in.get("messages"), systemParts);
        out.put("contents", contents);

        if (!systemParts.isEmpty()) {
            out.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", String.join("\n\n", systemParts)))
            ));
        }

        Map<String, Object> generationConfig = new HashMap<>();
        Double temperature = asDouble(in.get("temperature"));
        if (temperature != null) generationConfig.put("temperature", temperature);
        Double topP = asDouble(in.get("top_p"));
        if (topP != null) generationConfig.put("topP", topP);
        Integer topK = asInt(in.get("top_k"));
        if (topK != null) generationConfig.put("topK", topK);
        Integer maxTokens = asInt(in.get("max_tokens"));
        if (maxTokens == null) maxTokens = asInt(in.get("max_completion_tokens"));
        if (maxTokens != null) generationConfig.put("maxOutputTokens", maxTokens);

        List<String> stops = toStringList(in.get("stop"));
        if (!stops.isEmpty()) generationConfig.put("stopSequences", stops);

        if (!generationConfig.isEmpty()) out.put("generationConfig", generationConfig);
        return out;
    }

    private static List<Map<String, Object>> toGeminiContents(Object messages, List<String> systemParts) {
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

            String geminiRole = "assistant".equals(role) ? "model" : "user";
            Object contentObj = map.get("content");

            // Handle multimodal content array
            if (contentObj instanceof Iterable<?> contentList && hasImageParts(contentList)) {
                List<Map<String, Object>> parts = convertToGeminiParts(contentList);
                if (!parts.isEmpty()) {
                    out.add(Map.of("role", geminiRole, "parts", parts));
                }
                continue;
            }

            String content = extractText(contentObj);
            if (content.isEmpty()) continue;

            out.add(Map.of(
                    "role", geminiRole,
                    "parts", List.of(Map.of("text", content))
            ));
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

    private static List<Map<String, Object>> convertToGeminiParts(Iterable<?> parts) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object part : parts) {
            if (!(part instanceof Map<?, ?> map)) continue;
            String type = string(map.get("type"));
            if ("text".equals(type)) {
                String text = string(map.get("text"));
                if (!text.isEmpty()) {
                    out.add(Map.of("text", text));
                }
            } else if ("image_url".equals(type)) {
                Object imageUrlObj = map.get("image_url");
                if (imageUrlObj instanceof Map<?, ?> urlMap) {
                    String url = string(urlMap.get("url"));
                    if (!url.isEmpty()) {
                        // Gemini supports file_data with fileUri for URLs
                        out.add(Map.of(
                                "file_data", Map.of("file_uri", url, "mime_type", "image/jpeg")
                        ));
                    }
                }
            }
        }
        return out;
    }

    private static String extractCandidateText(JsonNode candidate) {
        if (candidate == null || candidate.isMissingNode() || candidate.isNull()) return null;
        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray()) return null;

        StringBuilder sb = new StringBuilder();
        for (JsonNode p : parts) {
            if (p == null || p.isNull()) continue;
            JsonNode text = p.get("text");
            if (text == null || text.isNull()) continue;
            String t = text.asText();
            if (t == null || t.isEmpty()) continue;
            sb.append(t);
        }
        return sb.toString();
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

    private static String normalizeModelPath(String model) {
        String m = model == null ? "" : model.trim();
        if (m.isEmpty()) return "models/gemini-1.5-flash";
        if (m.startsWith("models/")) return m;
        return "models/" + m;
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
            log.debug("[gemini] asInt parse failed: {}", v);
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
            log.debug("[gemini] asDouble parse failed: {}", v);
            return null;
        }
    }

    private static String string(Object v) {
        String t = v == null ? "" : String.valueOf(v).trim();
        return t == null ? "" : t;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        String b = baseUrl.trim();
        if (b.endsWith("/v1beta/")) return b.substring(0, b.length() - 8);
        if (b.endsWith("/v1beta")) return b.substring(0, b.length() - 7);
        return b;
    }
}

