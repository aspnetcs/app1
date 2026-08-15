package com.webchat.platformapi.ai.audio;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.credits.CreditsErrorSupport;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voice-chat")
@ConditionalOnProperty(name = "platform.voice-chat.enabled", havingValue = "true")
public class VoiceChatController {

    private static final Logger log = LoggerFactory.getLogger(VoiceChatController.class);
    private static final double FALLBACK_BYTES_PER_SECOND = 32000.0;

    private final VoiceChatService voiceChatService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;
    private final CreditsPolicyEvaluator creditsPolicyEvaluator;
    private final CreditsRuntimeService creditsRuntimeService;

    public VoiceChatController(
            VoiceChatService voiceChatService,
            UserGroupService userGroupService,
            @org.springframework.beans.factory.annotation.Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled,
            @org.springframework.lang.Nullable CreditsPolicyEvaluator creditsPolicyEvaluator,
            @org.springframework.lang.Nullable CreditsRuntimeService creditsRuntimeService
    ) {
        this.voiceChatService = voiceChatService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
        this.creditsPolicyEvaluator = creditsPolicyEvaluator;
        this.creditsRuntimeService = creditsRuntimeService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        VoiceChatProperties props = voiceChatService.getProperties();
        return ApiResponse.ok(Map.of(
                "enabled", props.isEnabled() && (!userGroupsEnabled || userGroupService.isFeatureAllowed(userId, "voice_chat")),
                "maxDurationSeconds", props.getMaxDurationSeconds(),
                "sttModel", props.getSttModel(),
                "ttsModel", props.getTtsModel(),
                "ttsVoice", props.getTtsVoice()
        ));
    }

    @PostMapping("/pipeline")
    public ApiResponse<Map<String, Object>> pipeline(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "history", required = false) String historyJson
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        if (audioFile == null || audioFile.isEmpty()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "audio file is required");
        }

        List<Map<String, Object>> history = null;
        if (historyJson != null && !historyJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(historyJson, List.class);
                history = parsed;
            } catch (Exception e) {
                log.debug("[voice-chat] invalid history json ignored: {}", e.toString());
            }
        }

        VoiceChatProperties props = voiceChatService.getProperties();
        if (!props.isEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "voice chat is disabled");
        }
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "voice_chat")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "voice chat not allowed by group policy");
        }
        long maxBytes = props.getMaxUploadBytes();
        if (maxBytes <= 0) {
            maxBytes = Long.MAX_VALUE;
        }

        long declaredSize = audioFile.getSize();
        if (maxBytes != Long.MAX_VALUE && declaredSize > 0 && declaredSize > maxBytes) {
            log.warn("[voice-chat] upload too large for user {}: declared={} limit={}", userId, declaredSize, maxBytes);
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "audio file is too large");
        }

        byte[] audioData;
        try {
            audioData = readAudioData(audioFile, maxBytes);
        } catch (IOException e) {
            log.warn("[voice-chat] audio read failed for user {}: {}", userId, e.getMessage());
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "audio file is too large or unreadable");
        }

        double durationSeconds = estimateAudioDuration(audioData);
        int maxDuration = props.getMaxDurationSeconds();
        if (maxDuration > 0 && durationSeconds > maxDuration) {
            log.warn("[voice-chat] audio duration {}s exceeds limit {}s for user {}", durationSeconds, maxDuration, userId);
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "audio duration exceeds limit");
        }

        // Credits policy evaluation
        Long creditsSnapshotId = null;
        if (creditsPolicyEvaluator != null) {
            String creditsModel = model != null ? model : "";
            String effectiveRole = role == null || role.isBlank() ? "user" : role;
            CreditsPolicyEvaluator.PolicyDecision decision = null;
            try {
                decision = creditsPolicyEvaluator.evaluate(userId, null, effectiveRole, creditsModel);
            } catch (Exception e) {
                log.warn("[voice-chat] credits policy check failed (fail-closed): userId={}, error={}", userId, e.toString());
                return CreditsErrorSupport.apiError("credits_policy_unavailable");
            }
            if (decision != null) {
                if (!decision.allowed()) {
                    return CreditsErrorSupport.apiError(decision.denialReason());
                }
                if (decision.creditsRequired() && creditsRuntimeService != null) {
                    String creditsRequestId = "voice_" + UUID.randomUUID().toString().replace("-", "");
                    try {
                        creditsSnapshotId = creditsRuntimeService.reserve(
                                userId,
                                null,
                                effectiveRole,
                                creditsModel,
                                decision.modelMeta(),
                                decision.account(),
                                creditsRequestId
                        );
                    } catch (Exception e) {
                        log.warn("[voice-chat] credits reserve failed: userId={}, error={}", userId, e.toString());
                        return CreditsErrorSupport.apiError("credits_reserve_failed");
                    }
                    if (creditsSnapshotId == null) {
                        return CreditsErrorSupport.apiError("credits_reserve_failed");
                    }
                }
            }
        }

        try {
            VoiceChatService.VoiceChatResult result = voiceChatService.pipeline(
                    userId,
                    audioData,
                    audioFile.getOriginalFilename(),
                    model,
                    history
            );
            if (!result.success()) {
                log.warn("[voice-chat] pipeline returned error for user {}: {}", userId, result.error());
                if (creditsSnapshotId != null && creditsRuntimeService != null) {
                    try { creditsRuntimeService.refund(creditsSnapshotId); } catch (Exception ignored) {}
                }
                return ApiResponse.error(ErrorCodes.SERVER_ERROR, "voice chat is temporarily unavailable");
            }
            // Credits settle on success
            if (creditsSnapshotId != null && creditsRuntimeService != null) {
                try { creditsRuntimeService.settle(creditsSnapshotId, 1, 0, 0, 0); } catch (Exception ignored) {}
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("transcript", result.transcript());
            response.put("aiResponse", result.aiResponse());
            if (result.audioBase64() != null) {
                response.put("audioBase64", result.audioBase64());
                response.put("contentType", result.contentType());
            }
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("[voice-chat] pipeline failed for user {}", userId, e);
            if (creditsSnapshotId != null && creditsRuntimeService != null) {
                try { creditsRuntimeService.refund(creditsSnapshotId); } catch (Exception ignored) {}
            }
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "voice chat is temporarily unavailable");
        }
    }

    private static byte[] readAudioData(MultipartFile audioFile, long maxBytes) throws IOException {
        try (InputStream in = audioFile.getInputStream(); java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            long totalRead = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                totalRead += read;
                if (totalRead > maxBytes) {
                    throw new IOException("audio exceeds configured limit");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static double estimateAudioDuration(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return 0d;
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioData))) {
            AudioFormat format = ais.getFormat();
            long frames = ais.getFrameLength();
            float frameRate = format.getFrameRate();
            if (frames > 0 && frameRate > 0) {
                return frames / frameRate;
            }
        } catch (UnsupportedAudioFileException | IOException e) {
            log.debug("[voice-chat] estimate duration fallback: {}", e.toString());
        }
        return audioData.length / FALLBACK_BYTES_PER_SECOND;
    }
}
