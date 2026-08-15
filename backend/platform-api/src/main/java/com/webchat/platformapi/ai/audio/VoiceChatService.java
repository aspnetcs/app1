package com.webchat.platformapi.ai.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.UrlUtil;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;

import com.webchat.platformapi.config.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Voice Chat pipeline service: STT -> LLM -> TTS.
 * Orchestrates the three steps in a single synchronous call.
 *
 * STT and TTS are delegated to {@link AiAudioService}.
 * Only the LLM chat step is handled internally (voice-specific system prompt and max_tokens).
 */
@Service
@ConditionalOnProperty(name = "platform.voice-chat.enabled", havingValue = "true")
public class VoiceChatService {

    private static final String CFG_VOICE_CHAT_ENABLED = "voicechat.enabled";
    private static final String CFG_VOICE_CHAT_MAX_DURATION = "voicechat.max_duration_seconds";
    private static final String CFG_VOICE_CHAT_STT_MODEL = "voicechat.stt_model";
    private static final String CFG_VOICE_CHAT_TTS_MODEL = "voicechat.tts_model";
    private static final String CFG_VOICE_CHAT_TTS_VOICE = "voicechat.tts_voice";

    private static final Logger log = LoggerFactory.getLogger(VoiceChatService.class);

    private final VoiceChatProperties properties;
    private final ChannelRouter channelRouter;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final AiAudioService audioService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public VoiceChatService(
            VoiceChatProperties properties,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            AiAudioService audioService,
            ObjectMapper objectMapper,
            SysConfigService sysConfigService
    ) {
        this.properties = properties;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.audioService = audioService;
        this.objectMapper = objectMapper;
        loadPersistedConfig(sysConfigService);
    }

    private void loadPersistedConfig(SysConfigService sysConfigService) {
        sysConfigService.get(CFG_VOICE_CHAT_ENABLED).ifPresent(v -> properties.setEnabled(Boolean.parseBoolean(v)));
        sysConfigService.get(CFG_VOICE_CHAT_MAX_DURATION).ifPresent(v -> {
            try { properties.setMaxDurationSeconds(Integer.parseInt(v)); } catch (NumberFormatException ignored) {}
        });
        sysConfigService.get(CFG_VOICE_CHAT_STT_MODEL).ifPresent(properties::setSttModel);
        sysConfigService.get(CFG_VOICE_CHAT_TTS_MODEL).ifPresent(properties::setTtsModel);
        sysConfigService.get(CFG_VOICE_CHAT_TTS_VOICE).ifPresent(properties::setTtsVoice);
    }

    /**
     * Full pipeline: audio bytes -> text (STT) -> AI response -> audio (TTS).
     */
    public VoiceChatResult pipeline(
            UUID userId,
            byte[] audioData,
            String fileName,
            String model,
            List<Map<String, Object>> history
    ) {
        // Step 1: STT -- delegate to AiAudioService
        AiAudioService.TranscribeResult sttResult = audioService.transcribe(
                userId, properties.getSttModel(), audioData, fileName
        );
        if (!sttResult.success()) {
            log.warn("[voice-chat] STT failed: {}", sttResult.error());
            return VoiceChatResult.error("语音识别失败: " + sttResult.error());
        }
        String transcript = sttResult.text();
        if (transcript == null || transcript.isBlank()) {
            return VoiceChatResult.error("未能识别到语音内容");
        }

        // Step 2: LLM -- get AI response (synchronous non-streaming, voice-specific)
        String aiResponse;
        try {
            aiResponse = chat(model, transcript, history);
        } catch (Exception e) {
            log.warn("[voice-chat] LLM failed: {}", e.getMessage());
            return VoiceChatResult.error("AI 回复失败: " + e.getMessage());
        }
        if (aiResponse == null || aiResponse.isBlank()) {
            return VoiceChatResult.error("AI 未返回回复");
        }

        // Step 3: TTS -- delegate to AiAudioService
        AiAudioService.SpeechResult ttsResult = audioService.synthesize(
                userId,
                properties.getTtsModel(),
                aiResponse,
                properties.getTtsVoice(),
                "mp3",
                1.0
        );
        if (!ttsResult.success()) {
            // Return text even if TTS fails
            return new VoiceChatResult(true, null, transcript, aiResponse, null, null);
        }

        return VoiceChatResult.ok(transcript, aiResponse, ttsResult.audioBase64(), ttsResult.contentType());
    }

    /**
     * Voice-specific LLM chat call with short-response system prompt.
     */
    private String chat(String model, String userMessage, List<Map<String, Object>> history) throws Exception {
        String safeModel = model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();

        ChannelSelection selection;
        try {
            selection = channelRouter.select(safeModel);
        } catch (NoChannelException e) {
            throw new IllegalStateException("暂无可用 LLM 通道");
        }

        String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
        ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个语音对话助手。请用简洁自然的口语化方式回答，每次回复不超过 100 字。"));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("model", selection.actualModel() != null ? selection.actualModel() : safeModel);
        bodyMap.put("messages", messages);
        bodyMap.put("stream", false);
        bodyMap.put("max_tokens", 200);

        String jsonBody = objectMapper.writeValueAsString(bodyMap);
        String url = UrlUtil.join(selection.channel().getBaseUrl(), "/v1/chat/completions");

        Duration timeout = configuredTimeout();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM upstream error: status=" + response.statusCode());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        if (choices == null || choices.isEmpty()) return "";

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return "";

        Object content = message.get("content");
        return content == null ? "" : String.valueOf(content).trim();
    }

    private Duration configuredTimeout() {
        int seconds = Math.max(30, properties.getMaxDurationSeconds());
        return Duration.ofSeconds(seconds);
    }

    public VoiceChatProperties getProperties() { return properties; }

    // --- Result DTO ---

    public record VoiceChatResult(
            boolean success,
            String error,
            String transcript,
            String aiResponse,
            String audioBase64,
            String contentType
    ) {
        public static VoiceChatResult ok(String transcript, String aiResponse, String audioBase64, String contentType) {
            return new VoiceChatResult(true, null, transcript, aiResponse, audioBase64, contentType);
        }
        public static VoiceChatResult error(String error) {
            return new VoiceChatResult(false, error, null, null, null, null);
        }
    }
}
