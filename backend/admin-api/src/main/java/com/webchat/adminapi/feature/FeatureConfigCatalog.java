package com.webchat.adminapi.feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FeatureConfigCatalog {

    private FeatureConfigCatalog() {
    }

    static Map<String, FeatureDefinition> createDefinitions() {
        Map<String, FeatureDefinition> map = new LinkedHashMap<>();
        register(map, new FeatureDefinition(
                "audio",
                List.of(
                        FeatureField.bool("enabled", "platform.audio.tts.enabled", false),
                        FeatureField.bool("browserFallbackEnabled", "platform.audio.tts.browser-fallback-enabled", true),
                        FeatureField.text("defaultModel", "platform.audio.tts.default-model", "tts-1"),
                        FeatureField.text("defaultVoice", "platform.audio.tts.default-voice", "alloy"),
                        FeatureField.text("responseFormat", "platform.audio.tts.response-format", "mp3"),
                        FeatureField.number("maxInputChars", "platform.audio.tts.max-input-chars", 2000, 1, null)
                )
        ));
        register(map, new FeatureDefinition(
                "mermaid",
                List.of(
                        FeatureField.bool("enabled", "platform.mermaid.enabled", false),
                        FeatureField.text("renderMode", null, "h5-render, non-h5 fallback-to-code", true)
                )
        ));
        register(map, new FeatureDefinition(
                "translation",
                List.of(
                        FeatureField.bool("enabled", "platform.translation.enabled", false),
                        FeatureField.text("defaultModel", "platform.translation.default-model", ""),
                        FeatureField.text("defaultTargetLanguage", "platform.translation.default-target-language", "English"),
                        FeatureField.number("maxInputChars", "platform.translation.max-input-chars", 8000, 1, null)
                )
        ));
        register(map, new FeatureDefinition(
                "web-read",
                List.of(
                        FeatureField.bool("enabled", "platform.web-read.enabled", false),
                        FeatureField.number("maxContentChars", "platform.web-read.max-content-chars", 8000, 1, null),
                        FeatureField.number("connectTimeoutMs", "platform.web-read.connect-timeout-ms", 8000, 1000, null),
                        FeatureField.bool("allowHttp", "platform.web-read.allow-http", false),
                        FeatureField.bool("enforceHostAllowlist", "platform.web-read.enforce-host-allowlist", true),
                        FeatureField.list("allowedHosts", "platform.web-read.allowed-hosts", List.of())
                )
        ));
        register(map, new FeatureDefinition(
                "follow-up",
                List.of(
                        FeatureField.bool("enabled", "platform.follow-up.enabled", false),
                        FeatureField.text("defaultModel", "platform.follow-up.default-model", ""),
                        FeatureField.number("maxSuggestions", "platform.follow-up.max-suggestions", 3, 1, null),
                        FeatureField.number("maxContextChars", "platform.follow-up.max-context-chars", 4000, 1, null)
                )
        ));
        register(map, new FeatureDefinition(
                "prompt-optimize",
                List.of(
                        FeatureField.bool("enabled", "platform.prompt-optimize.enabled", false),
                        FeatureField.text("defaultModel", "platform.prompt-optimize.default-model", ""),
                        FeatureField.text("defaultDirection", "platform.prompt-optimize.default-direction", "detailed"),
                        FeatureField.number("maxInputChars", "platform.prompt-optimize.max-input-chars", 8000, 1, null)
                )
        ));
        register(map, new FeatureDefinition(
                "pwa",
                List.of(
                        FeatureField.bool("enabled", "platform.pwa.enabled", false),
                        FeatureField.bool("forceRefresh", "platform.pwa.force-refresh", false),
                        FeatureField.text("manifestPath", null, "/manifest.webmanifest", true),
                        FeatureField.text("serviceWorkerPath", null, "/sw.js", true),
                        FeatureField.text("cacheStrategy", null, "static-assets", true)
                )
        ));
        register(map, new FeatureDefinition(
                "multi-chat",
                List.of(
                        FeatureField.bool("enabled", "platform.multi-chat.enabled", false),
                        FeatureField.number("maxModels", "platform.multi-chat.max-models", 3, 1, null),
                        FeatureField.list("notes", null, List.of(
                                "Single request fans out to multiple model responses.",
                                "Model-level availability is controlled by ai_model_metadata.multi_chat_enabled."
                        ), true)
                )
        ));
        register(map, new FeatureDefinition(
                "team-chat",
                List.of(
                        FeatureField.bool("enabled", "platform.team-chat.enabled", false),
                        FeatureField.number("maxModels", "platform.team-chat.max-models", 5, 2, 10),
                        FeatureField.number("maxDebateRounds", "platform.team-chat.max-debate-rounds", 1, 1, 5),
                        FeatureField.number("votingTimeoutSeconds", "platform.team-chat.voting-timeout-seconds", 60, 10, 300),
                        FeatureField.number("extractionTimeoutSeconds", "platform.team-chat.extraction-timeout-seconds", 120, 30, 600),
                        FeatureField.number("maxLlmConcurrency", "platform.team-chat.max-llm-concurrency", 8, 2, 20),
                        FeatureField.number("sharedSummaryMaxChars", "platform.team-chat.shared-summary-max-chars", 2000, 200, 10000),
                        FeatureField.number("memberMemoryMaxChars", "platform.team-chat.member-memory-max-chars", 1000, 200, 5000),
                        FeatureField.number("conversationTtlHours", "platform.team.conversation-ttl-hours", 72, 1, 720),
                        FeatureField.number("turnTtlMinutes", "platform.team.turn-ttl-minutes", 30, 5, 1440)
                )
        ));
        return map;
    }

    private static void register(Map<String, FeatureDefinition> map, FeatureDefinition definition) {
        map.put(definition.key(), definition);
    }
}
