package com.webchat.platformapi.ai.texttransform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FollowUpSuggestionService extends AbstractAiTextTransformService {

    private static final int MAX_SUGGESTION_CHARS = 32;

    private final boolean enabled;
    private final String defaultModel;
    private final int maxSuggestions;
    private final int maxContextChars;

    public FollowUpSuggestionService(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            @Value("${platform.follow-up.enabled:false}") boolean enabled,
            @Value("${platform.follow-up.default-model:}") String defaultModel,
            @Value("${platform.follow-up.max-suggestions:3}") int maxSuggestions,
            @Value("${platform.follow-up.max-context-chars:4000}") int maxContextChars
    ) {
        super(objectMapper, adapterFactory, channelRouter, cryptoService, ssrfGuard, channelMonitor);
        this.enabled = enabled;
        this.defaultModel = defaultModel;
        this.maxSuggestions = maxSuggestions;
        this.maxContextChars = maxContextChars;
    }

    public SuggestionResult suggest(UUID userId, String model, String context) {
        if (!enabled) return SuggestionResult.error("follow-up is disabled");
        String safeContext = context == null ? "" : context.trim();
        if (safeContext.isEmpty()) return SuggestionResult.error("context is required");
        if (safeContext.length() > maxContextChars) {
            safeContext = safeContext.substring(safeContext.length() - maxContextChars);
        }

        String targetModel = resolveModel(model, defaultModel);
        String systemPrompt = "你是追问推荐助手。请基于给定对话，只输出" + maxSuggestions
                + "条简短追问，每条不超过15字，每行一条，不要编号。";

        try {
            String content = callChatCompletion(targetModel, systemPrompt, safeContext, getFeatureLabel());
            List<String> suggestions = parseSuggestions(content);
            if (suggestions.isEmpty()) {
                return SuggestionResult.error("follow-up output is empty");
            }
            return SuggestionResult.ok(suggestions);
        } catch (NoChannelException e) {
            return SuggestionResult.error("暂无可用追问通道: " + (e.getMessage() == null ? "" : e.getMessage()));
        } catch (AiTextTransformException e) {
            return SuggestionResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[follow-up] suggest failed: {}", e.getMessage());
            return SuggestionResult.error("follow-up failed: " + (e.getMessage() == null ? "unknown" : e.getMessage()));
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getDefaultModel() { return defaultModel; }
    public int getMaxSuggestions() { return maxSuggestions; }
    public int getMaxContextChars() { return maxContextChars; }

    @Override
    protected String getFeatureLabel() { return "follow-up"; }

    private List<String> parseSuggestions(String content) {
        if (content == null || content.isBlank()) return List.of();
        List<String> suggestions = new ArrayList<>();
        for (String line : content.split("\\r?\\n")) {
            String text = line.replaceFirst("^[-*\\d.\\s]+", "").trim();
            if (text.isEmpty()) continue;
            if (text.length() > MAX_SUGGESTION_CHARS) {
                text = text.substring(0, MAX_SUGGESTION_CHARS).trim();
            }
            if (!suggestions.contains(text)) {
                suggestions.add(text);
            }
            if (suggestions.size() >= maxSuggestions) break;
        }
        return suggestions;
    }

    public record SuggestionResult(boolean success, String error, List<String> suggestions) {
        public static SuggestionResult ok(List<String> suggestions) {
            return new SuggestionResult(true, null, suggestions);
        }
        public static SuggestionResult error(String error) {
            return new SuggestionResult(false, error, List.of());
        }
    }
}
