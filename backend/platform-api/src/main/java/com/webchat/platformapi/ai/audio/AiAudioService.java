package com.webchat.platformapi.ai.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.UrlUtil;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AiAudioService {

    private static final Logger log = LoggerFactory.getLogger(AiAudioService.class);

    private final ObjectMapper objectMapper;
    private final ChannelRouter channelRouter;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final ChannelMonitor channelMonitor;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public AiAudioService(
            ObjectMapper objectMapper,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor
    ) {
        this.objectMapper = objectMapper;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.channelMonitor = channelMonitor;
    }

    // ===================== TTS (Text-to-Speech) =====================

    public SpeechResult synthesize(
            UUID userId,
            String model,
            String input,
            String voice,
            String responseFormat,
            double speed
    ) {
        if (input == null || input.isBlank()) {
            return SpeechResult.error("input is required");
        }
        String safeModel = model == null || model.isBlank() ? "tts-1" : model.trim();
        String safeVoice = voice == null || voice.isBlank() ? "alloy" : voice.trim();
        String safeFormat = responseFormat == null || responseFormat.isBlank() ? "mp3" : responseFormat.trim();
        double safeSpeed = Double.isFinite(speed) ? Math.max(0.25d, Math.min(speed, 4.0d)) : 1.0d;

        ChannelSelection selection;
        try {
            selection = channelRouter.select(safeModel);
        } catch (NoChannelException e) {
            return SpeechResult.error("暂无可用语音通道: " + (e.getMessage() == null ? "" : e.getMessage()));
        }

        try {
            String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
            ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("model", selection.actualModel() != null && !selection.actualModel().isBlank() ? selection.actualModel() : safeModel);
            bodyMap.put("input", input);
            bodyMap.put("voice", safeVoice);
            bodyMap.put("response_format", safeFormat);
            bodyMap.put("speed", safeSpeed);

            String jsonBody = objectMapper.writeValueAsString(bodyMap);
            String url = UrlUtil.join(selection.channel().getBaseUrl(), "/v1/audio/speech");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String preview = new String(response.body(), StandardCharsets.UTF_8);
                if (preview.length() > 300) preview = preview.substring(0, 300);
                log.warn("[ai-audio] upstream error: status={}, body={}", response.statusCode(), preview);
                monitorFailure(selection, "http_error", response.statusCode(), "status=" + response.statusCode());
                return SpeechResult.error("语音合成失败: upstream status " + response.statusCode());
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse(defaultContentType(safeFormat));
            monitorSuccess(selection);
            return SpeechResult.ok(
                    Base64.getEncoder().encodeToString(response.body()),
                    contentType,
                    safeModel,
                    safeVoice
            );
        } catch (SsrfGuard.SsrfException e) {
            monitorFailure(selection, "ssrf_blocked", null, e.getMessage());
            return SpeechResult.error("SSRF 拦截: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[ai-audio] synthesize failed: model={}, error={}", safeModel, e.getMessage());
            monitorFailure(selection, "exception", null, e.getMessage());
            return SpeechResult.error("语音合成失败: " + (e.getMessage() == null ? "unknown" : e.getMessage()));
        }
    }

    // ===================== STT (Speech-to-Text) =====================

    /**
     * Transcribe audio bytes to text using a Whisper-compatible STT API.
     *
     * @param userId    authenticated user
     * @param model     STT model name (e.g. "whisper-1"), defaults to "whisper-1" if blank
     * @param audioData raw audio bytes (webm/wav/mp3)
     * @param fileName  original filename for content type detection
     * @return transcription result
     */
    public TranscribeResult transcribe(UUID userId, String model, byte[] audioData, String fileName) {
        if (audioData == null || audioData.length == 0) {
            return TranscribeResult.error("audio data is required");
        }

        String safeModel = model == null || model.isBlank() ? "whisper-1" : model.trim();
        String safeFileName = fileName != null && !fileName.isBlank() ? fileName : "audio.webm";

        ChannelSelection selection;
        try {
            selection = channelRouter.select(safeModel);
        } catch (NoChannelException e) {
            return TranscribeResult.error("暂无可用 STT 通道: " + (e.getMessage() == null ? "" : e.getMessage()));
        }

        try {
            String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
            ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

            String url = UrlUtil.join(selection.channel().getBaseUrl(), "/v1/audio/transcriptions");
            String actualModel = selection.actualModel() != null ? selection.actualModel() : safeModel;
            String boundary = "----AiAudioBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] bodyBytes = buildMultipartBody(boundary, audioData, safeFileName, actualModel);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[ai-audio] STT upstream error: status={}", response.statusCode());
                monitorFailure(selection, "http_error", response.statusCode(), "status=" + response.statusCode());
                return TranscribeResult.error("语音识别失败: upstream status " + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Object text = result.get("text");
            String transcript = text == null ? "" : String.valueOf(text).trim();
            if (transcript.isEmpty()) {
                monitorFailure(selection, "empty_output", null, "empty transcription");
                return TranscribeResult.error("未能识别到语音内容");
            }

            monitorSuccess(selection);
            return TranscribeResult.ok(transcript, safeModel);
        } catch (SsrfGuard.SsrfException e) {
            monitorFailure(selection, "ssrf_blocked", null, e.getMessage());
            return TranscribeResult.error("SSRF 拦截: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[ai-audio] transcribe failed: model={}, error={}", safeModel, e.getMessage());
            monitorFailure(selection, "exception", null, e.getMessage());
            return TranscribeResult.error("语音识别失败: " + (e.getMessage() == null ? "unknown" : e.getMessage()));
        }
    }

    // ===================== Shared Utilities =====================

    private static String defaultContentType(String responseFormat) {
        if ("wav".equalsIgnoreCase(responseFormat)) return "audio/wav";
        if ("aac".equalsIgnoreCase(responseFormat)) return "audio/aac";
        if ("opus".equalsIgnoreCase(responseFormat)) return "audio/ogg";
        if ("flac".equalsIgnoreCase(responseFormat)) return "audio/flac";
        return "audio/mpeg";
    }

    /**
     * Build multipart/form-data body bytes for STT upload.
     */
    private static byte[] buildMultipartBody(String boundary, byte[] audioData, String fileName, String model) {
        StringBuilder sb = new StringBuilder();
        // file field
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");

        byte[] prefix = sb.toString().getBytes(StandardCharsets.UTF_8);

        StringBuilder sb2 = new StringBuilder();
        sb2.append("\r\n");
        // model field
        sb2.append("--").append(boundary).append("\r\n");
        sb2.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        sb2.append(model).append("\r\n");
        sb2.append("--").append(boundary).append("--\r\n");

        byte[] suffix = sb2.toString().getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[prefix.length + audioData.length + suffix.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(audioData, 0, result, prefix.length, audioData.length);
        System.arraycopy(suffix, 0, result, prefix.length + audioData.length, suffix.length);
        return result;
    }

    private void monitorSuccess(ChannelSelection selection) {
        if (channelMonitor == null || selection == null) return;
        try {
            channelMonitor.recordSuccess(
                    selection.channel() == null ? null : selection.channel().getId(),
                    selection.key() == null ? null : selection.key().getId());
        } catch (Exception e) {
            log.debug("[ai-audio] monitorSuccess failed: {}", e.getMessage());
        }
    }

    private void monitorFailure(ChannelSelection selection, String code, Integer httpStatus, String error) {
        if (channelMonitor == null || selection == null) return;
        try {
            channelMonitor.recordFailure(
                    selection.channel() == null ? null : selection.channel().getId(),
                    selection.key() == null ? null : selection.key().getId(),
                    code, httpStatus, error);
        } catch (Exception e) {
            log.debug("[ai-audio] monitorFailure failed: {}", e.getMessage());
        }
    }

    // ===================== Result DTOs =====================

    public record SpeechResult(boolean success, String error, String audioBase64, String contentType, String model, String voice) {
        public static SpeechResult ok(String audioBase64, String contentType, String model, String voice) {
            return new SpeechResult(true, null, audioBase64, contentType, model, voice);
        }
        public static SpeechResult error(String error) {
            return new SpeechResult(false, error, null, null, null, null);
        }
    }

    public record TranscribeResult(boolean success, String error, String text, String model) {
        public static TranscribeResult ok(String text, String model) {
            return new TranscribeResult(true, null, text, model);
        }
        public static TranscribeResult error(String error) {
            return new TranscribeResult(false, error, null, null);
        }
    }
}
