package com.webchat.platformapi.ai.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.openai.OpenAiAdapter;
import com.webchat.platformapi.ai.adapter.UrlUtil;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FunctionCallingService {

    private final ToolCatalogService toolCatalogService;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;
    private final OpenAiAdapter openAiAdapter;
    private final ToolRuntimeConfigService toolRuntimeConfigService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public FunctionCallingService(
            ToolCatalogService toolCatalogService,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper,
            OpenAiAdapter openAiAdapter,
            ToolRuntimeConfigService toolRuntimeConfigService
    ) {
        this.toolCatalogService = toolCatalogService;
        this.toolExecutionService = toolExecutionService;
        this.objectMapper = objectMapper;
        this.openAiAdapter = openAiAdapter;
        this.toolRuntimeConfigService = toolRuntimeConfigService;
    }

    public boolean supports(Map<String, Object> requestBody, String channelType) {
        ToolConfigProperties current = toolRuntimeConfigService.refresh();
        return current.isEnabled()
                && "openai".equalsIgnoreCase(channelType)
                && !requestedToolNames(requestBody).isEmpty();
    }

    public FunctionCallingOutcome execute(AiChannelEntity channel, String apiKey, Map<String, Object> requestBody) throws FunctionCallingException {
        ToolConfigProperties current = toolRuntimeConfigService.refresh();
        List<String> toolNames = requestedToolNames(requestBody);
        List<ToolDefinition> definitions = toolCatalogService.listEnabledTools(toolNames);
        if (definitions.isEmpty()) {
            throw new FunctionCallingException("no enabled tools available", false);
        }
        Map<String, ToolDefinition> definitionLookup = new LinkedHashMap<>();
        for (ToolDefinition definition : definitions) {
            definitionLookup.put(definition.name(), definition);
        }

        Map<String, Object> req = new LinkedHashMap<>();
        if (requestBody != null) req.putAll(requestBody);
        req.remove("stream");
        req.remove("toolNames");
        req.put("stream", false);
        req.put("tools", toOpenAiTools(definitions));
        req.put("tool_choice", "auto");

        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        List<Map<String, Object>> trace = new ArrayList<>();

        for (int step = 1; step <= Math.max(1, current.getMaxSteps()); step++) {
            JsonNode root = send(channel, apiKey, req);
            JsonNode usage = root.get("usage");
            if (usage != null && !usage.isNull()) {
                promptTokens += usage.path("prompt_tokens").asInt(0);
                completionTokens += usage.path("completion_tokens").asInt(0);
                totalTokens += usage.path("total_tokens").asInt(0);
            }

            JsonNode message = root.path("choices").path(0).path("message");
            if (message.isMissingNode() || message.isNull()) {
                throw new FunctionCallingException("missing assistant message", true);
            }

            String content = readAssistantContent(message.get("content"));
            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
                return new FunctionCallingOutcome(content, trace, promptTokens, completionTokens, totalTokens);
            }

            List<Map<String, Object>> messages = objectMapper.convertValue(req.get("messages"), new TypeReference<>() {});
            Map<String, Object> assistantMessage = new LinkedHashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", content == null ? "" : content);
            assistantMessage.put("tool_calls", objectMapper.convertValue(toolCalls, new TypeReference<List<Map<String, Object>>>() {}));
            messages.add(assistantMessage);

            for (JsonNode toolCall : toolCalls) {
                String callId = toolCall.path("id").asText("");
                JsonNode functionNode = toolCall.path("function");
                String toolName = functionNode.path("name").asText("");
                JsonNode argumentsNode = functionNode.path("arguments");
                String argText = argumentsNode == null || argumentsNode.isNull() ? "{}" : argumentsNode.toString();
                Map<String, Object> args = parseArgs(argumentsNode, definitionLookup.get(toolName));
                ToolExecutionService.ToolExecutionResult result;
                try {
                    result = toolExecutionService.execute(toolName, args);
                } catch (ToolExecutionService.ToolExecutionException e) {
                    throw new FunctionCallingException(e.getMessage(), false, e);
                }
                trace.add(Map.of(
                        "toolName", toolName,
                        "status", result.status(),
                        "input", argText,
                        "output", result.output()
                ));
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", callId,
                        "content", result.output()
                ));
            }
            req.put("messages", messages);
        }

        throw new FunctionCallingException("function calling exceeded max steps", false);
    }

    private JsonNode send(AiChannelEntity channel, String apiKey, Map<String, Object> requestBody) throws FunctionCallingException {
        try {
            HttpRequest request = openAiAdapter.buildChatRequest(requestBody, channel, apiKey, false);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FunctionCallingException("upstream status=" + response.statusCode(), true);
            }
            return objectMapper.readTree(response.body());
        } catch (FunctionCallingException e) {
            throw e;
        } catch (Exception e) {
            throw new FunctionCallingException("function calling request failed", true, e);
        }
    }

    private static List<String> requestedToolNames(Map<String, Object> requestBody) {
        Object raw = requestBody == null ? null : requestBody.get("toolNames");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .map(item -> item == null ? "" : String.valueOf(item).trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> toOpenAiTools(List<ToolDefinition> definitions) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition definition : definitions) {
            tools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", definition.name(),
                            "description", definition.description(),
                            "parameters", definition.parametersSchema()
                    )
            ));
        }
        return tools;
    }

    private Map<String, Object> parseArgs(JsonNode argumentsNode, ToolDefinition definition) throws FunctionCallingException {
        if (argumentsNode == null || argumentsNode.isNull()) return Map.of();
        JsonNode normalizedNode = argumentsNode;
        if (argumentsNode.isTextual()) {
            String raw = argumentsNode.asText("");
            if (raw == null || raw.trim().isEmpty()) {
                return Map.of();
            }
            try {
                normalizedNode = objectMapper.readTree(raw);
            } catch (Exception e) {
                String singlePropertyName = singlePropertyName(definition);
                if (singlePropertyName != null && !looksLikeStructuredJson(raw)) {
                    return Map.of(singlePropertyName, raw.trim());
                }
                throw new FunctionCallingException("invalid tool arguments", false, e);
            }
        }
        if (normalizedNode.isObject()) {
            try {
                return objectMapper.convertValue(normalizedNode, new TypeReference<LinkedHashMap<String, Object>>() {});
            } catch (IllegalArgumentException e) {
                throw new FunctionCallingException("invalid tool arguments", false, e);
            }
        }
        String singlePropertyName = singlePropertyName(definition);
        if (singlePropertyName != null && normalizedNode.isValueNode()) {
            return Map.of(singlePropertyName, objectMapper.convertValue(normalizedNode, Object.class));
        }
        throw new FunctionCallingException("invalid tool arguments", false);
    }

    private static String singlePropertyName(ToolDefinition definition) {
        if (definition == null || definition.parametersSchema() == null) {
            return null;
        }
        Object rawProperties = definition.parametersSchema().get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties) || properties.size() != 1) {
            return null;
        }
        Object key = properties.keySet().iterator().next();
        return key == null ? null : String.valueOf(key);
    }

    private static boolean looksLikeStructuredJson(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static String readAssistantContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) return "";
        if (contentNode.isTextual()) return contentNode.asText("");
        return contentNode.toString();
    }

    public record FunctionCallingOutcome(
            String content,
            List<Map<String, Object>> toolTrace,
            int promptTokens,
            int completionTokens,
            int totalTokens
    ) {
    }

    public static class FunctionCallingException extends Exception {
        private final boolean channelFailure;

        public FunctionCallingException(String message, boolean channelFailure) {
            super(message);
            this.channelFailure = channelFailure;
        }

        public FunctionCallingException(String message, boolean channelFailure, Throwable cause) {
            super(message, cause);
            this.channelFailure = channelFailure;
        }

        public boolean isChannelFailure() {
            return channelFailure;
        }
    }
}
