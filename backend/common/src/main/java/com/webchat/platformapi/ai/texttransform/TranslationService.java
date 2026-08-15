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

import java.util.UUID;

@Service
public class TranslationService extends AbstractAiTextTransformService {

    private static final String SYSTEM_PROMPT = "你是翻译助手。只输出译文，不要添加解释、引号或前缀。";

    private final boolean enabled;
    private final String defaultModel;
    private final String defaultTargetLanguage;
    private final int maxInputChars;

    public TranslationService(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            @Value("${platform.translation.enabled:false}") boolean enabled,
            @Value("${platform.translation.default-model:}") String defaultModel,
            @Value("${platform.translation.default-target-language:English}") String defaultTargetLanguage,
            @Value("${platform.translation.max-input-chars:8000}") int maxInputChars
    ) {
        super(objectMapper, adapterFactory, channelRouter, cryptoService, ssrfGuard, channelMonitor);
        this.enabled = enabled;
        this.defaultModel = defaultModel;
        this.defaultTargetLanguage = defaultTargetLanguage;
        this.maxInputChars = maxInputChars;
    }

    public TranslationResult translate(UUID userId, String model, String content, String targetLanguage) {
        if (!enabled) return TranslationResult.error("translation is disabled");
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) return TranslationResult.error("content is required");
        if (text.length() > maxInputChars) return TranslationResult.error("content exceeds max length");

        String safeTargetLanguage = normalizeLanguage(targetLanguage);
        String targetModel = resolveModel(model, defaultModel);

        try {
            String translated = callChatCompletion(
                    targetModel,
                    SYSTEM_PROMPT,
                    "请将以下内容翻译为" + safeTargetLanguage + "：\n\n" + text,
                    getFeatureLabel()
            );
            return TranslationResult.ok(translated, safeTargetLanguage);
        } catch (NoChannelException e) {
            return TranslationResult.error("暂无可用翻译通道: " + (e.getMessage() == null ? "" : e.getMessage()));
        } catch (AiTextTransformException e) {
            return TranslationResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[translation] translate failed: {}", e.getMessage());
            return TranslationResult.error("translation failed: " + (e.getMessage() == null ? "unknown" : e.getMessage()));
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getDefaultModel() { return defaultModel; }
    public String getDefaultTargetLanguage() { return defaultTargetLanguage; }
    public int getMaxInputChars() { return maxInputChars; }

    @Override
    protected String getFeatureLabel() { return "translation"; }

    private String normalizeLanguage(String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank()) return defaultTargetLanguage;
        return targetLanguage.trim();
    }

    public record TranslationResult(boolean success, String error, String translatedText, String targetLanguage) {
        public static TranslationResult ok(String translatedText, String targetLanguage) {
            return new TranslationResult(true, null, translatedText, targetLanguage);
        }
        public static TranslationResult error(String error) {
            return new TranslationResult(false, error, null, null);
        }
    }
}
