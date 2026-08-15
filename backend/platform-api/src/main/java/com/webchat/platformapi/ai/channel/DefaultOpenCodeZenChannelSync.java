package com.webchat.platformapi.ai.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.UrlUtil;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DefaultOpenCodeZenChannelSync {

    private static final Logger log = LoggerFactory.getLogger(DefaultOpenCodeZenChannelSync.class);

    private static final String CHANNEL_NAME = "OpenCode Zen";
    private static final String CHANNEL_TYPE = "openai";
    private static final String BASE_URL = "https://opencode.ai/zen";
    private static final String API_KEY = "public";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> EXCLUDED_MODEL_FAMILIES = List.of("claude", "gpt", "gemini");

    private final AiChannelRepository channelRepository;
    private final AiChannelKeyRepository keyRepository;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final ChannelMonitor channelMonitor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DefaultOpenCodeZenChannelSync(
            AiChannelRepository channelRepository,
            AiChannelKeyRepository keyRepository,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            ObjectMapper objectMapper
    ) {
        this.channelRepository = channelRepository;
        this.keyRepository = keyRepository;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.channelMonitor = channelMonitor;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            AiChannelEntity channel = ensureChannelAndKey();
            List<String> discoveredModels = fetchOpenAiModelIds(channel);
            List<String> workingModels = probeWorkingModels(channel, discoveredModels);
            applyWorkingModels(channel.getId(), workingModels);
            log.info(
                    "[ai.channel.seed] {} synced: discovered={}, working={}",
                    CHANNEL_NAME,
                    discoveredModels.size(),
                    workingModels.size()
            );
        } catch (Exception e) {
            log.warn("[ai.channel.seed] {} sync failed: {}", CHANNEL_NAME, e.toString());
        }
    }

    @Transactional
    protected AiChannelEntity ensureChannelAndKey() {
        AiChannelEntity channel = channelRepository
                .findFirstByNameAndTypeAndBaseUrl(CHANNEL_NAME, CHANNEL_TYPE, BASE_URL)
                .orElseGet(AiChannelEntity::new);
        boolean isNewChannel = channel.getId() == null;

        channel.setName(CHANNEL_NAME);
        channel.setType(CHANNEL_TYPE);
        channel.setBaseUrl(BASE_URL);
        channel.setPriority(-100);
        channel.setWeight(1);
        channel.setMaxConcurrent(4);
        if (isNewChannel) {
            channel.setEnabled(false);
            channel.setStatus(AiChannelStatus.DISABLED_AUTO);
        }
        ensureExtraConfig(channel).put("seeded", true);
        ensureExtraConfig(channel).put("model_sync", "startup");
        channel = channelRepository.save(channel);

        String keyHash = cryptoService.sha256Hex(API_KEY);
        AiChannelKeyEntity key = keyRepository
                .findFirstByChannel_IdAndKeyHash(channel.getId(), keyHash)
                .orElseGet(AiChannelKeyEntity::new);
        key.setChannel(channel);
        key.setKeyHash(keyHash);
        key.setApiKeyEncrypted(cryptoService.encrypt(API_KEY));
        key.setEnabled(true);
        key.setStatus(AiChannelStatus.NORMAL);
        key.setWeight(1);
        keyRepository.save(key);

        return channel;
    }

    private List<String> fetchOpenAiModelIds(AiChannelEntity channel) throws Exception {
        ssrfGuard.assertAllowedBaseUrl(channel.getBaseUrl());
        String url = UrlUtil.join(normalizeOpenAiApiPrefix(channel.getBaseUrl()), "/models");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("model list upstream " + response.statusCode() + ": " + safeBody(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode data = root.path("data");
        Set<String> modelIds = new LinkedHashSet<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                String id = item.path("id").asText("").trim();
                if (!id.isEmpty() && !isExcludedModel(id)) {
                    modelIds.add(id);
                }
            }
        }

        List<String> out = new ArrayList<>(modelIds);
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    private static boolean isExcludedModel(String modelId) {
        String normalized = modelId == null ? "" : modelId.toLowerCase(java.util.Locale.ROOT);
        for (String family : EXCLUDED_MODEL_FAMILIES) {
            if (normalized.contains(family)) {
                return true;
            }
        }
        return false;
    }

    private List<String> probeWorkingModels(AiChannelEntity channel, List<String> modelIds) {
        List<String> working = new ArrayList<>();
        for (String modelId : modelIds) {
            if (modelId == null || modelId.isBlank()) continue;
            ChannelMonitor.ProbeResult result = channelMonitor.probeChannel(channel.getId(), modelId, true);
            if (result.ok()) {
                working.add(modelId);
                log.info("[ai.channel.seed] {} model ok: {}", CHANNEL_NAME, modelId);
            } else {
                log.info("[ai.channel.seed] {} model skipped: {}, status={}, msg={}", CHANNEL_NAME, modelId, result.statusCode(), safeBody(result.message()));
            }
        }
        return working;
    }

    @Transactional
    protected void applyWorkingModels(Long channelId, List<String> workingModels) {
        AiChannelEntity channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) return;

        if (workingModels == null || workingModels.isEmpty()) {
            channel.setModels(null);
            channel.setTestModel(null);
            channel.setEnabled(false);
            channel.setStatus(AiChannelStatus.DISABLED_AUTO);
            ensureExtraConfig(channel).put("last_model_sync_status", "no_working_models");
            channelRepository.save(channel);
            return;
        }

        channel.setModels(String.join(",", workingModels));
        channel.setTestModel(workingModels.get(0));
        channel.setEnabled(true);
        channel.setStatus(AiChannelStatus.NORMAL);
        ensureExtraConfig(channel).put("last_model_sync_status", "ok");
        ensureExtraConfig(channel).put("last_model_sync_count", workingModels.size());
        channelRepository.save(channel);
    }

    private static java.util.Map<String, Object> ensureExtraConfig(AiChannelEntity channel) {
        if (channel.getExtraConfig() == null) {
            channel.setExtraConfig(new java.util.HashMap<>());
        }
        return channel.getExtraConfig();
    }

    private static String normalizeOpenAiApiPrefix(String baseUrl) {
        if (baseUrl == null) return "";
        String b = baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/v1") || b.endsWith("/v2")) return b;
        return b + "/v1";
    }

    private static String safeBody(String body) {
        String text = body == null ? "" : body.trim();
        if (text.length() > 240) {
            return text.substring(0, 240);
        }
        return text;
    }
}
