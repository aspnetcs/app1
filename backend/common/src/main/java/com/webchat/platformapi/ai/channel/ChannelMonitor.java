package com.webchat.platformapi.ai.channel;

import com.webchat.platformapi.ai.AiMonitorProperties;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.UrlUtil;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChannelMonitor {

    private static final Logger log = LoggerFactory.getLogger(ChannelMonitor.class);

    public record ProbeResult(
            boolean ok,
            int statusCode,
            String message,
            String url,
            long durationMs,
            Long channelId,
            Long keyId
    ) {
    }

    private final AiMonitorProperties properties;
    private final AiChannelRepository channelRepo;
    private final AiChannelKeyRepository keyRepo;
    private final AdapterFactory adapterFactory;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final HttpClient httpClient;

    public ChannelMonitor(
            AiMonitorProperties properties,
            AiChannelRepository channelRepo,
            AiChannelKeyRepository keyRepo,
            AdapterFactory adapterFactory,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard
    ) {
        this.properties = properties;
        this.channelRepo = channelRepo;
        this.keyRepo = keyRepo;
        this.adapterFactory = adapterFactory;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        int timeoutSec = properties == null ? 10 : Math.max(1, properties.getProbeTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Transactional
    public void recordSuccess(Long channelId, Long keyId) {
        if (properties == null || !properties.isEnabled()) return;
        try {
            if (channelId != null) channelRepo.markSuccess(channelId);
        } catch (Exception e) {
            log.warn("[ai.monitor] mark channel success failed: channelId={}, err={}", channelId, e.toString());
        }
        try {
            if (keyId != null) keyRepo.markSuccess(keyId);
        } catch (Exception e) {
            log.warn("[ai.monitor] mark key success failed: keyId={}, err={}", keyId, e.toString());
        }
    }

    @Transactional
    public void recordFailure(Long channelId, Long keyId, String failureCode, Integer httpStatus, String errorMessage) {
        if (properties == null || !properties.isEnabled()) return;
        FailureScope scope = classifyFailure(failureCode, httpStatus, errorMessage);

        if (scope.countChannel && channelId != null) {
            try {
                channelRepo.markFailure(channelId, scope.channelDisableAfter);
            } catch (Exception e) {
                log.warn("[ai.monitor] mark channel failure failed: channelId={}, err={}", channelId, e.toString());
            }
        }
        if (scope.countKey && keyId != null) {
            try {
                keyRepo.markFailure(keyId, scope.keyDisableAfter);
            } catch (Exception e) {
                log.warn("[ai.monitor] mark key failure failed: keyId={}, err={}", keyId, e.toString());
            }
        }
    }

    public ProbeResult probeChannel(Long channelId, String modelOverride, boolean recoverIfOk) {
        if (channelId == null) {
            return new ProbeResult(false, 0, "channelId is null", "", 0, null, null);
        }
        AiChannelEntity ch = channelRepo.findById(channelId).orElse(null);
        if (ch == null) {
            return new ProbeResult(false, 0, "channel not found", "", 0, channelId, null);
        }
        return probeChannelEntity(ch, modelOverride, recoverIfOk);
    }

    /**
     * Background recovery: periodically probe auto-disabled channels/keys and re-enable when probe succeeds.
     */
    @Scheduled(fixedDelayString = "${ai.monitor.probe-interval-ms:60000}")
    public void probeAutoDisabled() {
        if (properties == null || !properties.isEnabled()) return;

        Instant cutoff = Instant.now().minusSeconds(Math.max(1, properties.getRecoverAfterSeconds()));
        int limit = Math.max(1, properties.getMaxProbesPerRun());

        try {
            List<AiChannelEntity> channels = channelRepo.findAutoDisabledReadyToProbe(cutoff, limit);
            for (AiChannelEntity ch : channels) {
                if (ch == null || ch.getId() == null) continue;
                ProbeResult r = probeChannelEntity(ch, null, true);
                if (r.ok) {
                    log.info("[ai.monitor] recovered channel: channelId={}, keyId={}, url={}", r.channelId, r.keyId, r.url);
                } else {
                    log.info("[ai.monitor] probe channel failed: channelId={}, code={}, msg={}", ch.getId(), r.statusCode, safeMsg(r.message));
                }
            }
        } catch (Exception e) {
            log.warn("[ai.monitor] probe auto-disabled channels failed: {}", e.toString());
        }

        try {
            List<AiChannelKeyEntity> keys = keyRepo.findAutoDisabledReadyToProbe(cutoff, limit);
            for (AiChannelKeyEntity key : keys) {
                if (key == null || key.getId() == null) continue;
                Long channelId = key.getChannelId();
                if (channelId == null) continue;
                AiChannelEntity ch = channelRepo.findById(channelId).orElse(null);
                if (ch == null) continue;
                if (!ch.isEnabled()) continue;
                if (ch.getStatus() != AiChannelStatus.NORMAL) continue; // channel probe handles DISABLED_AUTO
                if (ch.getId() == null) continue;

                ProbeResult r = probeKeyEntity(ch, key, true);
                if (r.ok) {
                    log.info("[ai.monitor] recovered key: channelId={}, keyId={}, url={}", r.channelId, r.keyId, r.url);
                } else {
                    log.info("[ai.monitor] probe key failed: channelId={}, keyId={}, code={}, msg={}", ch.getId(), key.getId(), r.statusCode, safeMsg(r.message));
                }
            }
        } catch (Exception e) {
            log.warn("[ai.monitor] probe auto-disabled keys failed: {}", e.toString());
        }
    }

    private ProbeResult probeChannelEntity(AiChannelEntity channel, String modelOverride, boolean recoverIfOk) {
        String type = channel.getType() == null ? "" : channel.getType().trim().toLowerCase();
        if (type.isEmpty()) {
            return new ProbeResult(false, 0, "channel type is blank", "", 0, channel.getId(), null);
        }

        ProviderAdapter adapter;
        try {
            adapter = adapterFactory.get(type);
        } catch (Exception e) {
            return new ProbeResult(false, 0, "unsupported adapter type: " + type, "", 0, channel.getId(), null);
        }

        try {
            ssrfGuard.assertAllowedBaseUrl(channel.getBaseUrl());
        } catch (Exception e) {
            if (recoverIfOk) {
                recordFailure(channel.getId(), null, "ssrf_blocked", null, e.getMessage());
            }
            return new ProbeResult(false, 0, "ssrf_blocked: " + (e.getMessage() == null ? "" : e.getMessage()), "", 0, channel.getId(), null);
        }

        if ("openai".equals(adapter.type())) {
            return probeOpenAiChannel(channel, modelOverride, recoverIfOk);
        }
        if ("anthropic".equals(adapter.type()) || "gemini".equals(adapter.type())) {
            return probeGenericChannel(channel, adapter.type(), recoverIfOk);
        }
        return new ProbeResult(false, 0, "probe not supported for adapter: " + adapter.type(), "", 0, channel.getId(), null);
    }

    private ProbeResult probeKeyEntity(AiChannelEntity channel, AiChannelKeyEntity key, boolean recoverIfOk) {
        String type = channel == null ? "" : (channel.getType() == null ? "" : channel.getType().trim().toLowerCase());
        if (type.isEmpty()) return new ProbeResult(false, 0, "channel type is blank", "", 0, channel == null ? null : channel.getId(), key == null ? null : key.getId());

        ProviderAdapter adapter;
        try {
            adapter = adapterFactory.get(type);
        } catch (Exception e) {
            return new ProbeResult(false, 0, "unsupported adapter type: " + type, "", 0, channel.getId(), key.getId());
        }

        try {
            ssrfGuard.assertAllowedBaseUrl(channel.getBaseUrl());
        } catch (Exception e) {
            if (recoverIfOk) recordFailure(channel.getId(), key.getId(), "ssrf_blocked", null, e.getMessage());
            return new ProbeResult(false, 0, "ssrf_blocked: " + (e.getMessage() == null ? "" : e.getMessage()), "", 0, channel.getId(), key.getId());
        }

        if (!"openai".equals(adapter.type()) && !"anthropic".equals(adapter.type()) && !"gemini".equals(adapter.type())) {
            return new ProbeResult(false, 0, "key probe not supported for adapter: " + adapter.type(), "", 0, channel.getId(), key.getId());
        }

        String apiKey;
        try {
            apiKey = cryptoService.decrypt(key.getApiKeyEncrypted());
        } catch (Exception e) {
            if (recoverIfOk) recordFailure(channel.getId(), key.getId(), "decrypt_failed", null, e.getMessage());
            return new ProbeResult(false, 0, "decrypt_failed", "", 0, channel.getId(), key.getId());
        }

        return probeOpenAiModels(channel, key, apiKey, recoverIfOk);
    }

    private ProbeResult probeOpenAiChannel(AiChannelEntity channel, String modelOverride, boolean recoverIfOk) {
        List<AiChannelKeyEntity> keys;
        try {
            keys = keyRepo.findByChannel_IdOrderByIdAsc(channel.getId());
        } catch (Exception e) {
            keys = List.of();
        }

        List<AiChannelKeyEntity> candidates = new ArrayList<>();
        for (AiChannelKeyEntity k : keys) {
            if (k == null || k.getId() == null) continue;
            if (!k.isEnabled()) continue;
            if (k.getStatus() == AiChannelStatus.DISABLED_MANUAL) continue;
            candidates.add(k);
        }
        if (candidates.isEmpty()) {
            if (recoverIfOk) recordFailure(channel.getId(), null, "no_key", null, "no enabled key");
            return new ProbeResult(false, 0, "no enabled key for channel", "", 0, channel.getId(), null);
        }

        ProbeResult last = null;
        for (AiChannelKeyEntity key : candidates) {
            String apiKey;
            try {
                apiKey = cryptoService.decrypt(key.getApiKeyEncrypted());
            } catch (Exception e) {
                if (recoverIfOk) recordFailure(channel.getId(), key.getId(), "decrypt_failed", null, e.getMessage());
                last = new ProbeResult(false, 0, "decrypt_failed", "", 0, channel.getId(), key.getId());
                continue;
            }

            boolean probeChat = modelOverride != null && !modelOverride.isBlank();
            // When the caller provides a model, test real chat completions (not just /v1/models) to avoid
            // false positives where /v1/models works but /v1/chat/completions fails.
            ProbeResult r = probeOpenAiModels(channel, key, apiKey, recoverIfOk && !probeChat);
            last = r;
            if (r.ok && probeChat) {
                ProbeResult chat = probeOpenAiChat(channel, key, apiKey, modelOverride, recoverIfOk);
                last = chat;
                if (chat.ok) return chat;
                // Auth / rate-limit: try next key; other errors usually won't be fixed by switching key.
                if (chat.statusCode == 401 || chat.statusCode == 403 || chat.statusCode == 429) continue;
                break;
            }
            if (r.ok) return r;

            // Auth / rate-limit: try next key; other errors usually won't be fixed by switching key.
            if (r.statusCode == 401 || r.statusCode == 403 || r.statusCode == 429) continue;

            // Some proxies may not implement /v1/models. Try chat probe once.
            if (r.statusCode == 404 || r.statusCode == 405) {
                ProbeResult chat = probeOpenAiChat(channel, key, apiKey, modelOverride, recoverIfOk);
                last = chat;
                if (chat.ok) return chat;
            }
            break;
        }

        return last == null ? new ProbeResult(false, 0, "probe_failed", "", 0, channel.getId(), null) : last;
    }

    /**
     * Generic probe for Anthropic / Gemini channels.
     * Simply attempts to connect and decrypt a key to verify channel health.
     */
    private ProbeResult probeGenericChannel(AiChannelEntity channel, String adapterType, boolean recoverIfOk) {
        Long keyId = null;
        String apiKey = null;
        try {
            List<AiChannelKeyEntity> keys = keyRepo.findByChannel_IdOrderByIdAsc(channel.getId());
            for (AiChannelKeyEntity k : keys) {
                if (k == null || !k.isEnabled()) continue;
                if (k.getStatus() == AiChannelStatus.DISABLED_MANUAL) continue;
                try {
                    apiKey = cryptoService.decrypt(k.getApiKeyEncrypted());
                    keyId = k.getId();
                    break;
                } catch (Exception e) {
                    log.debug("[ai-monitor] decrypt failed for key {} on channel {}", k.getId(), channel.getId());
                }
            }
        } catch (Exception e) {
            log.warn("[ai-monitor] list keys for channel {} failed", channel.getId(), e);
        }

        if (keyId == null || apiKey == null) {
            if (recoverIfOk) recordFailure(channel.getId(), null, "no_key", null, "no enabled key or decrypt failed");
            return new ProbeResult(false, 0, "no enabled key for channel", "", 0, channel.getId(), null);
        }

        // Lightweight connectivity check: HEAD or GET to base URL
        String baseUrl = channel.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return new ProbeResult(false, 0, "base_url is blank", "", 0, channel.getId(), keyId);
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        // Anthropic: GET https://api.anthropic.com/v1/models (light check)
        // Gemini: GET https://generativelanguage.googleapis.com/v1beta/models
        String probeUrl;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(Math.max(1, properties.getProbeTimeoutSeconds())))
                .header("Accept", "application/json");

        if ("anthropic".equals(adapterType)) {
            probeUrl = baseUrl + "/v1/models";
            builder.header("x-api-key", apiKey.trim());
            builder.header("anthropic-version", "2023-06-01");
        } else {
            // gemini — use header instead of query param to avoid key leakage in logs/URLs
            probeUrl = baseUrl + "/v1beta/models";
            builder.header("x-goog-api-key", apiKey.trim());
        }

        Instant startedAt = Instant.now();
        int code = 0;
        String msg = "";
        try {
            builder.uri(URI.create(probeUrl));
            HttpResponse<String> resp = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            code = resp.statusCode();
            if (code >= 200 && code < 300) {
                if (recoverIfOk) recordSuccess(channel.getId(), keyId);
                return new ProbeResult(true, code, "ok", probeUrl, Duration.between(startedAt, Instant.now()).toMillis(), channel.getId(), keyId);
            }
            msg = safeBody(resp.body());
        } catch (Exception e) {
            msg = e.toString();
        }

        if (recoverIfOk) recordFailure(channel.getId(), keyId, "http_error", code == 0 ? null : code, msg);
        return new ProbeResult(false, code, msg, probeUrl, Duration.between(startedAt, Instant.now()).toMillis(), channel.getId(), keyId);
    }

    private ProbeResult probeOpenAiModels(AiChannelEntity channel, AiChannelKeyEntity key, String apiKey, boolean recoverIfOk) {
        // Some OpenAI-compatible providers expose the API under /v2 (not /v1).
        String apiPrefix = normalizeOpenAiApiPrefix(channel.getBaseUrl());
        String url = UrlUtil.join(apiPrefix, "/models");
        Instant startedAt = Instant.now();

        int code = 0;
        String msg = "";
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getProbeTimeoutSeconds())))
                    .header("Accept", "application/json");
            String authorization = buildAuthorizationHeader(apiKey);
            if (authorization != null) b.header("Authorization", authorization);
            HttpResponse<String> resp = httpClient.send(b.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            code = resp.statusCode();

            if (code >= 200 && code < 300) {
                // Some misconfigured endpoints return the OneAPI web UI HTML (still 200) which is a false positive.
                String body = resp.body();
                if (!looksLikeHtml(body)) {
                    if (recoverIfOk) recordSuccess(channel.getId(), key.getId());
                    return new ProbeResult(true, code, "ok", url, Duration.between(startedAt, Instant.now()).toMillis(), channel.getId(), key.getId());
                }
            }
            msg = safeBody(resp.body());
        } catch (Exception e) {
            msg = e.toString();
        }

        if (recoverIfOk) recordFailure(channel.getId(), key.getId(), "http_error", code == 0 ? null : code, msg);
        return new ProbeResult(false, code, msg, url, Duration.between(startedAt, Instant.now()).toMillis(), channel.getId(), key.getId());
    }

    private ProbeResult probeOpenAiChat(AiChannelEntity channel, AiChannelKeyEntity key, String apiKey, String modelOverride, boolean recoverIfOk) {
        String model = resolveProbeModel(channel, modelOverride);
        if (model == null || model.isBlank()) {
            return new ProbeResult(false, 0, "probe model is blank", "", 0, channel.getId(), key.getId());
        }

        ProviderAdapter adapter;
        try {
            adapter = adapterFactory.get("openai");
        } catch (Exception e) {
            return new ProbeResult(false, 0, "openai adapter missing", "", 0, channel.getId(), key.getId());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("max_tokens", 1);
        body.put("messages", List.of(Map.of("role", "user", "content", "ping")));

        Instant startedAt = Instant.now();
        int code = 0;
        String msg = "";
        String url = "";
        try {
            HttpRequest req = adapter.buildChatRequest(body, channel, apiKey, false);
            url = req.uri() == null ? "" : req.uri().toString();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            code = resp.statusCode();
            if (code >= 200 && code < 300) {
                if (recoverIfOk) recordSuccess(channel.getId(), key.getId());
                return new ProbeResult(true, code, "ok", url, Duration.between(startedAt, Instant.now()).toMillis(), channel.getId(), key.getId());
            }
            msg = safeBody(resp.body());
        } catch (Exception e) {
            msg = e.toString();
        }

        if (recoverIfOk) recordFailure(channel.getId(), key.getId(), "http_error", code == 0 ? null : code, msg);
        return new ProbeResult(false, code, msg, url, Duration.between(startedAt, Instant.now()).toMillis(), channel.getId(), key.getId());
    }

    private record FailureScope(boolean countChannel, boolean countKey, int channelDisableAfter, int keyDisableAfter) {
    }

    private FailureScope classifyFailure(String failureCode, Integer httpStatus, String errorMessage) {
        int channelAfter = properties == null ? 10 : Math.max(1, properties.getChannelDisableAfter());
        int keyAfter = properties == null ? 10 : Math.max(1, properties.getKeyDisableAfter());

        if ("ssrf_blocked".equalsIgnoreCase(failureCode)) {
            return new FailureScope(true, false, channelAfter, keyAfter);
        }
        if ("decrypt_failed".equalsIgnoreCase(failureCode)) {
            // Local config error -- disable after fewer attempts but still not instantly
            return new FailureScope(false, true, channelAfter, Math.max(3, keyAfter));
        }
        if ("no_key".equalsIgnoreCase(failureCode)) {
            return new FailureScope(true, false, channelAfter, keyAfter);
        }

        if (httpStatus != null) {
            // 401/403: key auth failure -- use configured threshold, NOT instant disable.
            // Many relay proxies return 401 on /models but accept chat requests fine.
            if (httpStatus == 401 || httpStatus == 403) return new FailureScope(false, true, channelAfter, keyAfter);
            if (httpStatus == 429) return new FailureScope(false, true, channelAfter, keyAfter);
        }

        // default: channel failure
        return new FailureScope(true, false, channelAfter, keyAfter);
    }

    private static String normalizeOpenAiApiPrefix(String baseUrl) {
        if (baseUrl == null) return "";
        String b = baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/v1") || b.endsWith("/v2")) return b;
        return b + "/v1";
    }

    private static String buildAuthorizationHeader(String apiKey) {
        if (apiKey == null) return null;
        String token = apiKey.trim();
        if (token.isEmpty()) return null;
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) return token;
        if (token.regionMatches(true, 0, "Basic ", 0, 6)) return token;
        return "Bearer " + token;
    }

    private static boolean looksLikeHtml(String body) {
        if (body == null) return false;
        String t = body.trim();
        if (t.isEmpty()) return false;
        String lowered = t.length() > 20 ? t.substring(0, 20).toLowerCase(java.util.Locale.ROOT) : t.toLowerCase(java.util.Locale.ROOT);
        return lowered.startsWith("<!doctype") || lowered.startsWith("<html") || lowered.startsWith("<head") || lowered.startsWith("<body");
    }

    private static String resolveProbeModel(AiChannelEntity channel, String modelOverride) {
        String m = modelOverride == null ? "" : modelOverride.trim();
        if (!m.isEmpty()) return m;

        String tm = channel == null ? "" : (channel.getTestModel() == null ? "" : channel.getTestModel().trim());
        if (!tm.isEmpty()) return tm;

        String models = channel == null ? "" : (channel.getModels() == null ? "" : channel.getModels().trim());
        if (!models.isEmpty()) {
            for (String part : models.split(",")) {
                String t = part == null ? "" : part.trim();
                if (!t.isEmpty()) return t;
            }
        }
        return "";
    }

    private static String safeMsg(String s) {
        String t = s == null ? "" : s.trim();
        if (t.length() > 200) t = t.substring(0, 200);
        return t;
    }

    private static String safeBody(String body) {
        String t = body == null ? "" : body.trim();
        if (t.length() > 800) t = t.substring(0, 800);
        return t;
    }
}
