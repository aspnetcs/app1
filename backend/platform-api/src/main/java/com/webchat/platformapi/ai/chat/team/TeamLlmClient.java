package com.webchat.platformapi.ai.chat.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.ChatKnowledgeContextService;
import com.webchat.platformapi.ai.chat.ChatSkillContextService;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.UUID;

/**
 * Internal LLM client for team debate non-streaming calls.
 * Used for VOTING (Scheme C judge calls) and EXTRACTING (captain issue extraction).
 * <p>
 * Modeled after {@code ResearchLlmClient} - uses ChannelRouter + AdapterFactory
 * with retry and channel/key exclusion logic.
 */
@Service
public class TeamLlmClient {

    private static final Logger log = LoggerFactory.getLogger(TeamLlmClient.class);
    private static final int MAX_RETRY = 2;

    private HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private AdapterFactory adapterFactory;
    @Autowired
    private ChannelRouter channelRouter;
    @Autowired
    private AiCryptoService cryptoService;
    @Autowired
    private SsrfGuard ssrfGuard;
    @Autowired
    private AiUsageService aiUsageService;
    @Autowired
    private ChatSkillContextService chatSkillContextService;
    @Autowired
    private ChatKnowledgeContextService chatKnowledgeContextService;

    /** Required by Spring's class-based proxy instantiation path. */
    protected TeamLlmClient() {
    }

    TeamLlmClient(
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            AiUsageService aiUsageService,
            ChatSkillContextService chatSkillContextService,
            ChatKnowledgeContextService chatKnowledgeContextService,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.adapterFactory = adapterFactory;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.aiUsageService = aiUsageService;
        this.chatSkillContextService = chatSkillContextService;
        this.chatKnowledgeContextService = chatKnowledgeContextService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a non-streaming chat completion request to a specific model.
     *
     * @param model        model ID to route through (e.g. "gpt-4o")
     * @param systemPrompt system message content
     * @param userPrompt   user message content
     * @param maxTokens    max output tokens
     * @param temperature  sampling temperature
     * @return assistant's reply text, never null
     * @throws TeamLlmException if all retries fail
     */
    public String complete(String model, String systemPrompt, String userPrompt,
                           int maxTokens, double temperature) {
        return complete(null, model, systemPrompt, userPrompt, maxTokens, temperature).content();
    }

    public TeamLlmResult complete(UUID userId, String model, String systemPrompt, String userPrompt,
                                  int maxTokens, double temperature) {
        Map<String, Object> body = prepareRequestBody(userId, model, systemPrompt, userPrompt, maxTokens, temperature, null);
        return executeWithRetry(body, model, userId);
    }

    public TeamLlmResult complete(UUID userId, String model, String systemPrompt, String userPrompt,
                                  List<String> knowledgeBaseIds) {
        return complete(userId, model, systemPrompt, userPrompt, 4096, 0.3, knowledgeBaseIds);
    }

    public TeamLlmResult complete(UUID userId, String model, String systemPrompt, String userPrompt,
                                  int maxTokens, double temperature, List<String> knowledgeBaseIds) {
        Map<String, Object> body = prepareRequestBody(userId, model, systemPrompt, userPrompt, maxTokens, temperature, knowledgeBaseIds);
        return executeWithRetry(body, model, userId);
    }

    /**
     * Convenience overload with default maxTokens=4096 and temperature=0.3.
     */
    public String complete(String model, String systemPrompt, String userPrompt) {
        return complete(model, systemPrompt, userPrompt, 4096, 0.3);
    }

    public TeamLlmResult complete(UUID userId, String model, String systemPrompt, String userPrompt) {
        return complete(userId, model, systemPrompt, userPrompt, 4096, 0.3);
    }

    public TeamLlmResult completeStreaming(
            UUID userId,
            String model,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            double temperature,
            Consumer<String> onDelta
    ) {
        Map<String, Object> body = prepareRequestBody(userId, model, systemPrompt, userPrompt, maxTokens, temperature, null);
        body.put("stream", true);
        return executeStreamingWithRetry(body, model, userId, onDelta);
    }

    public TeamLlmResult completeStreaming(
            UUID userId,
            String model,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            double temperature,
            List<String> knowledgeBaseIds,
            Consumer<String> onDelta
    ) {
        Map<String, Object> body = prepareRequestBody(userId, model, systemPrompt, userPrompt, maxTokens, temperature, knowledgeBaseIds);
        body.put("stream", true);
        return executeStreamingWithRetry(body, model, userId, onDelta);
    }

    Map<String, Object> prepareRequestBody(
            UUID userId,
            String model,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            double temperature,
            List<String> knowledgeBaseIds
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("stream", false);
        if (knowledgeBaseIds != null) {
            body.put("knowledgeBaseIds", new ArrayList<>(knowledgeBaseIds));
        }
        if (chatSkillContextService != null) {
            chatSkillContextService.applySavedSkillContracts(userId, body);
        }
        if (chatKnowledgeContextService != null) {
            chatKnowledgeContextService.applyKnowledgeContext(userId, body);
        }
        return body;
    }

    TeamLlmResult parseResponse(String responseBody, Long channelId, String channelType, String model, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = extractContent(root, responseBody);
            UsageStats usage = extractUsage(root);
            return new TeamLlmResult(
                    content,
                    usage.promptTokens(),
                    usage.completionTokens(),
                    latencyMs,
                    channelId,
                    channelType,
                    model
            );
        } catch (Exception e) {
            log.warn("[team-llm] failed to parse non-stream response: {}", e.getMessage());
            return new TeamLlmResult(responseBody, 0, 0, latencyMs, channelId, channelType, model);
        }
    }

    private TeamLlmResult executeWithRetry(Map<String, Object> body, String model, UUID userId) {
        Set<Long> excludedChannels = new HashSet<>();
        Set<Long> excludedKeys = new HashSet<>();

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                long startedAt = System.nanoTime();
                ChannelSelection selection = channelRouter.select(model, excludedChannels, excludedKeys);
                ProviderAdapter adapter = adapterFactory.get(selection.channel().getType());
                String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
                ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

                Map<String, Object> req = new LinkedHashMap<>(body);
                if (selection.actualModel() != null) {
                    req.put("model", selection.actualModel());
                }

                HttpRequest httpReq = adapter.buildChatRequest(req, selection.channel(), apiKey, false);
                HttpResponse<String> resp = httpClient.send(httpReq,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    String resolvedModel = selection.actualModel() != null && !selection.actualModel().isBlank()
                            ? selection.actualModel()
                            : model;
                    TeamLlmResult result = parseResponse(
                            resp.body(),
                            selection.channel().getId(),
                            selection.channel().getType(),
                            resolvedModel,
                            Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
                    );
                    logUsage(userId, result);
                    return result;
                }

                log.warn("[team-llm] attempt {} returned status {} for model {}",
                        attempt, resp.statusCode(), model);
                Long chId = selection.channel().getId();
                Long kId = selection.key().getId();
                if (resp.statusCode() == 401 || resp.statusCode() == 403 || resp.statusCode() == 429) {
                    if (kId != null) excludedKeys.add(kId);
                } else {
                    if (chId != null) excludedChannels.add(chId);
                }
            } catch (Exception e) {
                log.warn("[team-llm] attempt {} failed for model {}: [{}] {}",
                        attempt, model, e.getClass().getSimpleName(), e.getMessage());
                if (e.getCause() != null) {
                    log.warn("[team-llm]   caused by: [{}] {}",
                            e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
                }
            }
        }
        throw new TeamLlmException("Team LLM call failed after " + MAX_RETRY + " attempts for model: " + model);
    }

    private TeamLlmResult executeStreamingWithRetry(
            Map<String, Object> body,
            String model,
            UUID userId,
            Consumer<String> onDelta
    ) {
        Set<Long> excludedChannels = new HashSet<>();
        Set<Long> excludedKeys = new HashSet<>();

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            boolean sentAnyDelta = false;
            try {
                long startedAt = System.nanoTime();
                ChannelSelection selection = channelRouter.select(model, excludedChannels, excludedKeys);
                ProviderAdapter adapter = adapterFactory.get(selection.channel().getType());
                String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
                ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

                Map<String, Object> req = new LinkedHashMap<>(body);
                if (selection.actualModel() != null) {
                    req.put("model", selection.actualModel());
                }

                HttpRequest httpReq = adapter.buildChatRequest(req, selection.channel(), apiKey, true);
                HttpResponse<InputStream> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());

                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    Long channelId = selection.channel().getId();
                    Long keyId = selection.key().getId();
                    if (resp.statusCode() == 401 || resp.statusCode() == 403 || resp.statusCode() == 429) {
                        if (keyId != null) {
                            excludedKeys.add(keyId);
                        }
                    } else if (channelId != null) {
                        excludedChannels.add(channelId);
                    }
                    continue;
                }

                StringBuilder content = new StringBuilder();
                int promptTokens = 0;
                int completionTokens = 0;
                int totalTokens = 0;

                try (InputStream bodyStream = resp.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        StreamChunk chunk = adapter.parseStreamLine(line);
                        if (chunk == null) {
                            continue;
                        }
                        if (chunk.errorMessage() != null && !chunk.errorMessage().isBlank()) {
                            throw new TeamLlmException(chunk.errorMessage());
                        }
                        if (chunk.promptTokens() != null) {
                            promptTokens = chunk.promptTokens();
                        }
                        if (chunk.completionTokens() != null) {
                            completionTokens = chunk.completionTokens();
                        }
                        if (chunk.totalTokens() != null) {
                            totalTokens = chunk.totalTokens();
                        }
                        if (chunk.delta() != null && !chunk.delta().isEmpty()) {
                            content.append(chunk.delta());
                            if (onDelta != null) {
                                onDelta.accept(chunk.delta());
                            }
                            sentAnyDelta = true;
                        }
                        if (chunk.done()) {
                            break;
                        }
                    }
                }

                String resolvedModel = selection.actualModel() != null && !selection.actualModel().isBlank()
                        ? selection.actualModel()
                        : model;
                TeamLlmResult result = new TeamLlmResult(
                        content.toString(),
                        promptTokens,
                        completionTokens,
                        Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                        selection.channel().getId(),
                        selection.channel().getType(),
                        resolvedModel
                );
                logUsage(userId, result);
                return result;
            } catch (Exception e) {
                if (sentAnyDelta) {
                    throw new TeamLlmException("Team streaming LLM call failed after partial output: " + e.getMessage());
                }
                log.warn("[team-llm] streaming attempt {} failed for model {}: {}", attempt, model, e.getMessage());
            }
        }
        throw new TeamLlmException("Team streaming LLM call failed after " + MAX_RETRY + " attempts for model: " + model);
    }

    private void logUsage(UUID userId, TeamLlmResult result) {
        if (userId == null || aiUsageService == null || result == null) {
            return;
        }
        aiUsageService.logUsage(
                userId,
                result.channelId(),
                result.channelType(),
                result.model(),
                result.promptTokens(),
                result.completionTokens(),
                result.latencyMs(),
                true,
                UUID.randomUUID().toString()
        );
    }

    private String extractContent(JsonNode root, String responseBody) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText("");
        }
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText("");
            }
        }
        JsonNode content = root.path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        return responseBody;
    }

    private UsageStats extractUsage(JsonNode root) {
        JsonNode openAiUsage = root.path("usage");
        if (!openAiUsage.isMissingNode() && !openAiUsage.isNull()) {
            if (openAiUsage.has("prompt_tokens") || openAiUsage.has("completion_tokens")) {
                return new UsageStats(
                        openAiUsage.path("prompt_tokens").asInt(0),
                        openAiUsage.path("completion_tokens").asInt(0)
                );
            }
            if (openAiUsage.has("input_tokens") || openAiUsage.has("output_tokens")) {
                return new UsageStats(
                        openAiUsage.path("input_tokens").asInt(0),
                        openAiUsage.path("output_tokens").asInt(0)
                );
            }
        }

        JsonNode geminiUsage = root.path("usageMetadata");
        if (!geminiUsage.isMissingNode() && !geminiUsage.isNull()) {
            return new UsageStats(
                    geminiUsage.path("promptTokenCount").asInt(0),
                    geminiUsage.path("candidatesTokenCount").asInt(0)
            );
        }
        return new UsageStats(0, 0);
    }

    private record UsageStats(int promptTokens, int completionTokens) {
    }

    public record TeamLlmResult(
            String content,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            Long channelId,
            String channelType,
            String model
    ) {
    }

    public static class TeamLlmException extends RuntimeException {
        public TeamLlmException(String message) {
            super(message);
        }
    }
}
