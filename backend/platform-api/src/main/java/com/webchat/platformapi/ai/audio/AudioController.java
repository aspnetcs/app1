package com.webchat.platformapi.ai.audio;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.auth.group.UserGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audio")
@ConditionalOnProperty(name = "platform.audio.enabled", havingValue = "true")
public class AudioController {

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

    private final AiAudioService audioService;
    private final UserGroupService userGroupService;
    private final boolean ttsEnabled;
    private final boolean sttEnabled;
    private final int maxInputChars;
    private final String defaultTtsModel;
    private final String defaultTtsVoice;
    private final String responseFormat;
    private final boolean userGroupsEnabled;

    public AudioController(
            AiAudioService audioService,
            UserGroupService userGroupService,
            @Value("${platform.audio.tts.enabled:false}") boolean ttsEnabled,
            @Value("${platform.audio.stt.enabled:false}") boolean sttEnabled,
            @Value("${platform.audio.tts.max-input-chars:2000}") int maxInputChars,
            @Value("${platform.audio.tts.default-model:tts-1}") String defaultTtsModel,
            @Value("${platform.audio.tts.default-voice:alloy}") String defaultTtsVoice,
            @Value("${platform.audio.tts.response-format:mp3}") String responseFormat,
            @Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.audioService = audioService;
        this.userGroupService = userGroupService;
        this.ttsEnabled = ttsEnabled;
        this.sttEnabled = sttEnabled;
        this.maxInputChars = maxInputChars;
        this.defaultTtsModel = defaultTtsModel;
        this.defaultTtsVoice = defaultTtsVoice;
        this.responseFormat = responseFormat;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @PostMapping("/speech")
    public ApiResponse<Map<String, Object>> speech(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        if (!isAnyFeatureAllowed(userId, "tts", "voice_chat")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "tts not allowed by group policy");
        }
        if (!ttsEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "TTS is disabled");
        }
        if (body == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "input is required");
        }

        String input = valueOf(body, "input", "text", "content");
        if (input == null || input.isBlank()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "input is required");
        }
        String trimmedInput = input.trim();
        if (trimmedInput.length() > maxInputChars) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "input exceeds max length");
        }

        String model = valueOf(body, "model");
        String voice = valueOf(body, "voice");
        String format = valueOf(body, "response_format", "responseFormat");
        double speed = numberOf(body.get("speed"), 1.0d);

        AiAudioService.SpeechResult result = audioService.synthesize(
                userId,
                model == null ? defaultTtsModel : model,
                trimmedInput,
                voice == null ? defaultTtsVoice : voice,
                format == null ? responseFormat : format,
                speed
        );
        if (!result.success()) {
            log.warn("[audio] speech synthesis failed for user {}: {}", userId, result.error());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "speech synthesis failed");
        }
        return ApiResponse.ok(Map.of(
                "audio_base64", result.audioBase64(),
                "content_type", result.contentType(),
                "model", result.model(),
                "voice", result.voice()
        ));
    }

    @PostMapping("/transcriptions")
    public ApiResponse<Map<String, Object>> transcriptions(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "model", required = false) String model
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        if (!isAnyFeatureAllowed(userId, "stt", "voice_chat")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "stt not allowed by group policy");
        }
        if (!sttEnabled) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "STT is disabled");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "audio file is required");
        }

        byte[] audioData;
        try {
            audioData = file.getBytes();
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "failed to read audio file");
        }

        AiAudioService.TranscribeResult result = audioService.transcribe(
                userId,
                model,
                audioData,
                file.getOriginalFilename()
        );
        if (!result.success()) {
            log.warn("[audio] transcription failed for user {}: {}", userId, result.error());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "transcription failed");
        }
        return ApiResponse.ok(Map.of(
                "text", result.text(),
                "model", result.model()
        ));
    }

    private boolean isAnyFeatureAllowed(UUID userId, String... featureKeys) {
        if (!userGroupsEnabled || featureKeys == null || featureKeys.length == 0) {
            return true;
        }
        for (String featureKey : featureKeys) {
            if (userGroupService.isFeatureAllowed(userId, featureKey)) {
                return true;
            }
        }
        return false;
    }

    private static String valueOf(Map<String, Object> body, String... keys) {
        if (body == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || !body.containsKey(key)) {
                continue;
            }
            Object value = body.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static double numberOf(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
