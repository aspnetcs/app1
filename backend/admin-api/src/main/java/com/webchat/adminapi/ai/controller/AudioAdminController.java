package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audio")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class AudioAdminController {

    private final boolean audioEnabled;
    private final boolean ttsEnabled;
    private final boolean browserFallbackEnabled;
    private final String defaultModel;
    private final String defaultVoice;
    private final String responseFormat;
    private final int maxInputChars;

    public AudioAdminController(
            @Value("${platform.audio.enabled:false}") boolean audioEnabled,
            @Value("${platform.audio.tts.enabled:false}") boolean ttsEnabled,
            @Value("${platform.audio.tts.browser-fallback-enabled:true}") boolean browserFallbackEnabled,
            @Value("${platform.audio.tts.default-model:tts-1}") String defaultModel,
            @Value("${platform.audio.tts.default-voice:alloy}") String defaultVoice,
            @Value("${platform.audio.tts.response-format:mp3}") String responseFormat,
            @Value("${platform.audio.tts.max-input-chars:2000}") int maxInputChars
    ) {
        this.audioEnabled = audioEnabled;
        this.ttsEnabled = ttsEnabled;
        this.browserFallbackEnabled = browserFallbackEnabled;
        this.defaultModel = defaultModel;
        this.defaultVoice = defaultVoice;
        this.responseFormat = responseFormat;
        this.maxInputChars = maxInputChars;
    }

    @GetMapping("/tts-config")
    public ApiResponse<Map<String, Object>> ttsConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        return ApiResponse.ok(Map.of(
                "enabled", audioEnabled && ttsEnabled,
                "featureKey", "platform.audio.tts.enabled",
                "defaultModel", defaultModel,
                "defaultVoice", defaultVoice,
                "responseFormat", responseFormat,
                "maxInputChars", maxInputChars,
                "browserFallbackEnabled", browserFallbackEnabled
        ));
    }
}



