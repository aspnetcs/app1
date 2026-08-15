package com.webchat.platformapi.ai.texttransform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.AdapterUtils;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all AI text transformation services (translation,
 * prompt optimization, follow-up suggestions).
 *
 * Subclasses only need to provide the system prompt, user prompt construction,
 * and any post-processing logic. All infrastructure concerns (channel routing,
 * adapter selection, HTTP calls, response parsing, monitoring) are handled here.
 */
public abstract class AbstractAiTextTransformService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ObjectMapper objectMapper;
    protected final AdapterFactory adapterFactory;
    protected final ChannelRouter channelRouter;
    protected final AiCryptoService cryptoService;
    protected final SsrfGuard ssrfGuard;
    protected final ChannelMonitor channelMonitor;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    protected AbstractAiTextTransformService(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor
    ) {
        this.objectMapper = objectMapper;
        this.adapterFactory = adapterFactory;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.channelMonitor = channelMonitor;
    }

    /**
     * Resolve target model with fallback to default.
     */
    protected String resolveModel(String model, String defaultModel) {
        String target = model == null || model.isBlank() ? defaultModel : model.trim();
        if (target == null || target.isBlank()) target = "gpt-4o-mini";
        return target;
    }

    /**
     * Core method: send a non-streaming ChatCompletion request and return the text output.
     *
     * @param targetModel   the resolved model name
     * @param systemPrompt  system message content
     * @param userContent   user message content
     * @param featureLabel  label for logging and error messages (e.g. "translation")
     * @return the extracted text from the AI response, or null if empty
     * @throws NoChannelException if no channel is available for the model
     */
    protected String callChatCompletion(String targetModel, String systemPrompt, String userContent, String featureLabel)
            throws Exception {

        ChannelSelection selection = channelRouter.select(targetModel);

        try {
            var adapter = adapterFactory.get(selection.channel().getType());
            String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
            ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", selection.actualModel() != null && !selection.actualModel().isBlank()
                    ? selection.actualModel() : targetModel);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
            ));

            HttpRequest req = adapter.buildChatRequest(body, selection.channel(), apiKey, false);
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                monitorFailure(selection, "http_error", resp.statusCode(), "status=" + resp.statusCode());
                throw new AiTextTransformException(featureLabel + " upstream status " + resp.statusCode());
            }

            JsonNode root = objectMapper.readTree(resp.body());
            String output = extractText(root);
            if (output == null || output.isBlank()) {
                monitorFailure(selection, "empty_output", null, "empty " + featureLabel + " output");
                throw new AiTextTransformException(featureLabel + " output is empty");
            }
            monitorSuccess(selection);
            return output.trim();
        } catch (SsrfGuard.SsrfException e) {
            monitorFailure(selection, "ssrf_blocked", null, e.getMessage());
            throw new AiTextTransformException("SSRF blocked: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text content from a ChatCompletion JSON response.
     * Supports OpenAI, Anthropic, and Gemini response formats.
     */
    protected static String extractText(JsonNode root) {
        if (root == null) return null;

        String fromChoices = AdapterUtils.extractTextContent(root.path("choices"));
        if (fromChoices != null && !fromChoices.isBlank()) return fromChoices;

        JsonNode content = root.path("content");
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : content) {
                if ("text".equals(node.path("type").asText()) && node.has("text")) {
                    if (!builder.isEmpty()) builder.append('\n');
                    builder.append(node.get("text").asText());
                }
            }
            if (!builder.isEmpty()) return builder.toString();
        }

        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (parts.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : parts) {
                if (node.has("text")) {
                    if (!builder.isEmpty()) builder.append('\n');
                    builder.append(node.get("text").asText());
                }
            }
            if (!builder.isEmpty()) return builder.toString();
        }

        return null;
    }

    protected void monitorSuccess(ChannelSelection selection) {
        if (channelMonitor == null || selection == null) return;
        try {
            channelMonitor.recordSuccess(
                    selection.channel() == null ? null : selection.channel().getId(),
                    selection.key() == null ? null : selection.key().getId());
        } catch (Exception e) {
            log.debug("[{}] monitorSuccess failed: {}", getFeatureLabel(), e.getMessage());
        }
    }

    protected void monitorFailure(ChannelSelection selection, String code, Integer httpStatus, String error) {
        if (channelMonitor == null || selection == null) return;
        try {
            channelMonitor.recordFailure(
                    selection.channel() == null ? null : selection.channel().getId(),
                    selection.key() == null ? null : selection.key().getId(),
                    code, httpStatus, error);
        } catch (Exception e) {
            log.debug("[{}] monitorFailure failed: {}", getFeatureLabel(), e.getMessage());
        }
    }

    /**
     * Return a short label for this feature, used in log messages.
     */
    protected abstract String getFeatureLabel();

    /**
     * Exception type for text transform failures, allowing callers to
     * distinguish business errors from infrastructure errors.
     */
    public static class AiTextTransformException extends Exception {
        public AiTextTransformException(String message) {
            super(message);
        }

        public AiTextTransformException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
