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

import java.util.Locale;
import java.util.UUID;

@Service
public class PromptOptimizeService extends AbstractAiTextTransformService {

    private final boolean enabled;
    private final String defaultModel;
    private final String defaultDirection;
    private final int maxInputChars;

    public PromptOptimizeService(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            @Value("${platform.prompt-optimize.enabled:false}") boolean enabled,
            @Value("${platform.prompt-optimize.default-model:}") String defaultModel,
            @Value("${platform.prompt-optimize.default-direction:detailed}") String defaultDirection,
            @Value("${platform.prompt-optimize.max-input-chars:8000}") int maxInputChars
    ) {
        super(objectMapper, adapterFactory, channelRouter, cryptoService, ssrfGuard, channelMonitor);
        this.enabled = enabled;
        this.defaultModel = defaultModel;
        this.defaultDirection = defaultDirection;
        this.maxInputChars = maxInputChars;
    }

    public OptimizeResult optimize(UUID userId, String model, String content, String direction) {
        if (!enabled) return OptimizeResult.error("prompt optimize is disabled");
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) return OptimizeResult.error("content is required");
        if (text.length() > maxInputChars) return OptimizeResult.error("content exceeds max length");

        String safeDirection = normalizeDirection(direction);
        String targetModel = resolveModel(model, defaultModel);

        try {
            String optimized = callChatCompletion(targetModel, buildSystemPrompt(safeDirection), text, getFeatureLabel());
            return OptimizeResult.ok(optimized, safeDirection);
        } catch (NoChannelException e) {
            return OptimizeResult.error("暂无可用优化通道: " + (e.getMessage() == null ? "" : e.getMessage()));
        } catch (AiTextTransformException e) {
            return OptimizeResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[prompt-optimize] optimize failed: {}", e.getMessage());
            return OptimizeResult.error("optimize failed: " + (e.getMessage() == null ? "unknown" : e.getMessage()));
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getDefaultModel() { return defaultModel; }
    public String getDefaultDirection() { return normalizeDirection(defaultDirection); }
    public int getMaxInputChars() { return maxInputChars; }

    @Override
    protected String getFeatureLabel() { return "prompt-optimize"; }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) return "detailed";
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "detailed", "concise", "creative", "academic" -> direction.trim().toLowerCase(Locale.ROOT);
            default -> "detailed";
        };
    }

    private static String buildSystemPrompt(String direction) {
        String goal = switch (direction) {
            case "concise" -> "更简洁、更清晰，但保持原意。";
            case "creative" -> "更有创意、更有表现力，同时保留用户原意。";
            case "academic" -> "更严谨、更学术，补充结构、约束和输出要求。";
            default -> "更专业、更详细，补充角色定义、上下文、输出格式和约束条件。";
        };
        return "你是 Prompt 优化器。请将用户输入优化为更适合 AI 执行的高质量提示词。保持原意，不要回答任务本身，不要解释你的修改，只输出优化后的提示词。" + goal;
    }

    public record OptimizeResult(boolean success, String error, String optimizedPrompt, String direction) {
        public static OptimizeResult ok(String optimizedPrompt, String direction) {
            return new OptimizeResult(true, null, optimizedPrompt, direction);
        }
        public static OptimizeResult error(String error) {
            return new OptimizeResult(false, error, null, null);
        }
    }
}
