package com.webchat.platformapi.research.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.research.ResearchProperties;
import com.webchat.platformapi.research.ResearchRuntimeConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Thin LLM client for the research pipeline.
 * Uses the existing ChannelRouter and AdapterFactory to route calls
 * through configured AI channels (non-streaming only).
 */
@Service
public class ResearchLlmClient {

    private static final Logger log = LoggerFactory.getLogger(ResearchLlmClient.class);
    private static final int MAX_RETRY = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdapterFactory adapterFactory;
    private final ChannelRouter channelRouter;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final ResearchRuntimeConfigService runtimeConfigService;

    public ResearchLlmClient(
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ResearchRuntimeConfigService runtimeConfigService
    ) {
        this.adapterFactory = adapterFactory;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.runtimeConfigService = runtimeConfigService;
    }

    /**
     * Language-matching rule appended to every system prompt.
     * Ensures the LLM responds in the same language as the user's research topic.
     */
    private static final String LANGUAGE_RULE =
        "\n\nCRITICAL LANGUAGE RULE: Detect the language of the user's research topic. "
        + "Your ENTIRE response -- all reasoning, analysis, field names, and output text -- "
        + "MUST be written in that SAME language. "
        + "If the topic is in Chinese, respond entirely in Chinese. "
        + "If in English, respond entirely in English. "
        + "Never mix languages unless quoting a proper noun or citation.";

    /**
     * Send a non-streaming chat completion request and return the assistant's text.
     *
     * @param systemPrompt system message content
     * @param userPrompt   user message content
     * @return assistant's reply text, never null
     * @throws ResearchLlmException if all retries fail
     */
    public String complete(String systemPrompt, String userPrompt) {
        ResearchProperties properties = runtimeConfigService.snapshot();
        String model = resolveModel(properties);
        int maxTokens = properties.getLlm().getMaxTokens();
        double temperature = properties.getLlm().getTemperature();

        String enrichedPrompt = (systemPrompt == null ? "" : systemPrompt) + LANGUAGE_RULE;
        Map<String, Object> body = buildRequestBody(enrichedPrompt, userPrompt, model, maxTokens, temperature);
        return executeWithRetry(body, model);
    }

    /**
     * Complete with explicit model and parameter overrides.
     */
    public String complete(String systemPrompt, String userPrompt, String model,
                           int maxTokens, double temperature) {
        String enrichedPrompt = (systemPrompt == null ? "" : systemPrompt) + LANGUAGE_RULE;
        Map<String, Object> body = buildRequestBody(enrichedPrompt, userPrompt, model, maxTokens, temperature);
        return executeWithRetry(body, model);
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt,
                                                  String model, int maxTokens, double temperature) {
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
        return body;
    }

    String resolveModel(ResearchProperties properties) {
        if (properties != null && properties.getLlm() != null) {
            String configured = properties.getLlm().getModel();
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
        }

        for (var channel : channelRouter.listRoutableChannels()) {
            if (channel == null) {
                continue;
            }
            String models = channel.getModels();
            if (models != null && !models.isBlank()) {
                for (String candidate : models.split(",")) {
                    String trimmed = candidate == null ? "" : candidate.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
            Map<String, String> mapping = channel.getModelMapping();
            if (mapping != null) {
                for (String candidate : mapping.keySet()) {
                    String trimmed = candidate == null ? "" : candidate.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
        }
        return "";
    }

    private String executeWithRetry(Map<String, Object> body, String model) {
        Set<Long> excludedChannels = new HashSet<>();
        Set<Long> excludedKeys = new HashSet<>();

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                ChannelSelection selection = channelRouter.select(model, excludedChannels, excludedKeys);
                ProviderAdapter adapter = adapterFactory.get(selection.channel().getType());
                String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
                ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

                Map<String, Object> req = new LinkedHashMap<>(body);
                if (selection.actualModel() != null) {
                    req.put("model", selection.actualModel());
                }

                HttpRequest httpReq = adapter.buildChatRequest(req, selection.channel(), apiKey, false);
                HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return extractContent(resp.body());
                }

                log.warn("[research-llm] attempt {} returned status {} body={}", attempt, resp.statusCode(),
                    resp.body() != null && resp.body().length() > 300 ? resp.body().substring(0, 300) : resp.body());
                Long chId = selection.channel().getId();
                Long kId = selection.key().getId();
                if (resp.statusCode() == 401 || resp.statusCode() == 403 || resp.statusCode() == 429) {
                    if (kId != null) excludedKeys.add(kId);
                } else {
                    if (chId != null) excludedChannels.add(chId);
                }
            } catch (Exception e) {
                log.warn("[research-llm] attempt {} failed: {}", attempt, e.getMessage());
            }

            // backoff before next retry
            if (attempt < MAX_RETRY) {
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new ResearchLlmException("LLM call failed after " + MAX_RETRY + " attempts");
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText("");
            }
            // Gemini format
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText("");
                }
            }
            return responseBody;
        } catch (Exception e) {
            log.warn("[research-llm] failed to parse response: {}", e.getMessage());
            return responseBody;
        }
    }

    public static class ResearchLlmException extends RuntimeException {
        public ResearchLlmException(String message) {
            super(message);
        }
    }
}
