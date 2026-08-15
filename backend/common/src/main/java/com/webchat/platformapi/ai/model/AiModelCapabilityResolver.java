package com.webchat.platformapi.ai.model;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Resolves effective model capabilities by combining manual overrides
 * with inference rules based on model ID patterns.
 */
@Component
public class AiModelCapabilityResolver {

    private static final Set<String> KNOWN_VISION_PATTERNS = Set.of(
            "gpt-4o", "gpt-4-turbo", "gpt-4-vision",
            "gpt-5", "gpt-5.4",
            "claude-3", "claude-4",
            "gemini-1.5", "gemini-2", "gemini-pro-vision",
            "qwen-vl", "qwen2-vl",
            "glm-4v",
            "yi-vision",
            "llava",
            "pixtral",
            "internvl"
    );

    private static final Set<String> KNOWN_TEXT_ONLY_PATTERNS = Set.of(
            "gpt-3.5", "gpt-4-0314", "gpt-4-0613",
            "text-davinci", "text-curie", "text-babbage", "text-ada",
            "claude-2", "claude-instant",
            "deepseek-chat", "deepseek-coder",
            "qwen-turbo", "qwen-plus", "qwen-max",
            "yi-34b", "yi-6b",
            "mistral", "mixtral",
            "codellama", "llama-2", "llama-3"
    );

    public record ImageCapability(boolean supportsImageParsing, String source) {}

    /**
     * Resolves the effective image parsing capability for a model.
     *
     * Priority:
     * 1. Manual override (admin-set)
     * 2. Inference from model ID pattern
     * 3. Unknown -> false
     */
    public ImageCapability resolve(String modelId, Boolean manualOverride) {
        if (manualOverride != null) {
            return new ImageCapability(manualOverride, "manual");
        }

        String lower = modelId == null ? "" : modelId.toLowerCase();

        for (String pattern : KNOWN_VISION_PATTERNS) {
            if (lower.contains(pattern)) {
                return new ImageCapability(true, "inferred");
            }
        }

        for (String pattern : KNOWN_TEXT_ONLY_PATTERNS) {
            if (lower.contains(pattern)) {
                return new ImageCapability(false, "inferred");
            }
        }

        return new ImageCapability(false, "unknown");
    }
}
