package com.webchat.platformapi.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

final class GatewayProxyService {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final AdapterFactory adapterFactory;
    private final ChannelRouter channelRouter;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final ChannelMonitor channelMonitor;
    private final HttpClient httpClient;
    private final GatewayQuotaService gatewayQuotaService;
    private final com.webchat.platformapi.credits.CreditsRuntimeService creditsRuntimeService;

    GatewayProxyService(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            HttpClient httpClient,
            GatewayQuotaService gatewayQuotaService,
            com.webchat.platformapi.credits.CreditsRuntimeService creditsRuntimeService
    ) {
        this.objectMapper = objectMapper;
        this.adapterFactory = adapterFactory;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.channelMonitor = channelMonitor;
        this.httpClient = httpClient;
        this.gatewayQuotaService = gatewayQuotaService;
        this.creditsRuntimeService = creditsRuntimeService;
    }

    SseEmitter streamChat(SseEmitter emitter, Map<String, Object> body, String model, UUID userId,
                          Function<Runnable, Boolean> submitTask, Long creditsSnapshotId) {
        Map<String, Object> requestBody = new HashMap<>();
        if (body != null) {
            requestBody.putAll(body);
        }

        long reservedTokens;
        try {
            reservedTokens = gatewayQuotaService.reserveStreamingQuota(userId, requestBody);
        } catch (IllegalStateException e) {
            sendError(emitter, e.getMessage());
            return emitter;
        }

        if (!Boolean.TRUE.equals(submitTask.apply(() -> {
            Set<Long> excludedChannels = new HashSet<>();
            Set<Long> excludedKeys = new HashSet<>();
            boolean reservationFinalized = false;
            boolean anyDeltaSent = false;
            String requestId = "gw_" + UUID.randomUUID().toString().replace("-", "");

            try {
                for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                    int promptTokens = 0;
                    int completionTokens = 0;
                    int totalTokens = 0;
                    boolean sentAnyDelta = false;
                    ChannelSelection selection;
                    try {
                        selection = channelRouter.select(model, excludedChannels, excludedKeys);
                    } catch (NoChannelException e) {
                        sendError(emitter, "No available channel for model: " + model);
                        return;
                    }

                    Long chId = selection.channel() == null ? null : selection.channel().getId();
                    Long kId = selection.key() == null ? null : selection.key().getId();

                    try {
                        ProviderAdapter adapter = adapterFactory.get(selection.channel().getType());
                        String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
                        ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

                        Map<String, Object> req = new HashMap<>(requestBody);
                        req.put("stream", true);
                        if (selection.actualModel() != null) {
                            req.put("model", selection.actualModel());
                        }

                        HttpRequest upstreamReq = adapter.buildChatRequest(req, selection.channel(), apiKey, true);
                        HttpResponse<InputStream> resp = httpClient.send(upstreamReq, HttpResponse.BodyHandlers.ofInputStream());

                        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                            channelMonitor.recordFailure(chId, kId, "http_error", resp.statusCode(), "status=" + resp.statusCode());
                            excludeForError(resp.statusCode(), chId, kId, excludedChannels, excludedKeys);
                            resp.body().close();
                            if (attempt < MAX_RETRY_ATTEMPTS && !sentAnyDelta) {
                                continue;
                            }
                            if (reservedTokens > 0) {
                                gatewayQuotaService.protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                        promptTokens, completionTokens, totalTokens, sentAnyDelta);
                            }
                            sendError(emitter, "upstream returned status " + resp.statusCode());
                            return;
                        }

                        try (InputStream is = resp.body();
                             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            boolean upstreamError = false;
                            while ((line = br.readLine()) != null) {
                                StreamChunk chunk;
                                try {
                                    chunk = adapter.parseStreamLine(line);
                                } catch (Exception e) {
                                    continue;
                                }
                                if (chunk == null) {
                                    continue;
                                }

                                if (chunk.errorMessage() != null && !chunk.errorMessage().isBlank()) {
                                    channelMonitor.recordFailure(chId, kId, "upstream_error", null, chunk.errorMessage());
                                    if (!sentAnyDelta && attempt < MAX_RETRY_ATTEMPTS) {
                                        if (chId != null) {
                                            excludedChannels.add(chId);
                                        }
                                        upstreamError = true;
                                        break;
                                    }
                                    if (reservedTokens > 0) {
                                        gatewayQuotaService.protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                                promptTokens, completionTokens, totalTokens, sentAnyDelta);
                                    }
                                    sendError(emitter, chunk.errorMessage());
                                    return;
                                }
                                if (chunk.delta() != null && !chunk.delta().isEmpty()) {
                                    String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                                    Map<String, Object> delta = new LinkedHashMap<>();
                                    delta.put("content", chunk.delta());
                                    Map<String, Object> choice = new LinkedHashMap<>();
                                    choice.put("index", 0);
                                    choice.put("delta", delta);
                                    choice.put("finish_reason", null);
                                    Map<String, Object> openAiChunk = new LinkedHashMap<>();
                                    openAiChunk.put("id", id);
                                    openAiChunk.put("object", "chat.completion.chunk");
                                    openAiChunk.put("model", model);
                                    openAiChunk.put("choices", java.util.List.of(choice));
                                    emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(openAiChunk)));
                                    sentAnyDelta = true;
                                    anyDeltaSent = true;
                                }
                                if (chunk.promptTokens() != null) {
                                    promptTokens = chunk.promptTokens();
                                }
                                if (chunk.completionTokens() != null) {
                                    completionTokens = chunk.completionTokens();
                                }
                                if (chunk.totalTokens() != null) {
                                    totalTokens = chunk.totalTokens();
                                }
                                if (chunk.done()) {
                                    break;
                                }
                            }
                            if (upstreamError) {
                                continue;
                            }
                        }

                        try {
                            int finalTotal = totalTokens > 0 ? totalTokens : (promptTokens + completionTokens);
                            if (reservedTokens > 0) {
                                gatewayQuotaService.finalizeReservedQuotaAfterStream(userId, reservedTokens, chId, model,
                                        promptTokens, completionTokens, finalTotal, requestId, finalTotal > 0, sentAnyDelta);
                            } else {
                                gatewayQuotaService.persistUsageAndDeductQuota(userId, chId, model,
                                        promptTokens, completionTokens, totalTokens, requestId);
                            }
                            reservationFinalized = true;
                            // Credits settle on success
                            if (creditsSnapshotId != null && creditsRuntimeService != null) {
                                try { creditsRuntimeService.settle(creditsSnapshotId, 1, promptTokens, promptTokens, completionTokens); }
                                catch (Exception ignored) {}
                            }
                        } catch (Exception e) {
                            sendError(emitter, "usage persistence failed");
                            return;
                        }
                        channelMonitor.recordSuccess(chId, kId);
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                        return;
                    } catch (Exception e) {
                        log.debug("[v1] stream attempt {} failed: {}", attempt, e.getMessage());
                        if (chId != null) {
                            excludedChannels.add(chId);
                        }
                        if (kId != null) {
                            excludedKeys.add(kId);
                        }
                        if (sentAnyDelta) {
                            if (reservedTokens > 0) {
                                gatewayQuotaService.protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                        promptTokens, completionTokens, totalTokens, true);
                            }
                            sendError(emitter, e.getMessage());
                            return;
                        }
                        if (attempt >= MAX_RETRY_ATTEMPTS) {
                            sendError(emitter, e.getMessage());
                            return;
                        }
                    }
                }
                sendError(emitter, "all retry attempts exhausted");
            } finally {
                if (reservedTokens > 0 && !reservationFinalized && !anyDeltaSent) {
                    try {
                        gatewayQuotaService.releaseReservedQuota(userId, reservedTokens);
                    } catch (RuntimeException e) {
                        log.warn("[v1] release reserved quota failed: userId={}, requestId={}, error={}",
                                userId, requestId, e.toString());
                    }
                }
                // Credits final safety net
                if (creditsSnapshotId != null && !reservationFinalized && creditsRuntimeService != null) {
                    try {
                        if (anyDeltaSent) {
                            creditsRuntimeService.partialSettle(creditsSnapshotId, 0, 0);
                        } else {
                            creditsRuntimeService.refund(creditsSnapshotId);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }))) {
            if (reservedTokens > 0) {
                try {
                    gatewayQuotaService.releaseReservedQuota(userId, reservedTokens);
                } catch (RuntimeException e) {
                    log.warn("[v1] release reserved quota failed after executor rejection: userId={}, error={}",
                            userId, e.toString());
                }
            }
            sendError(emitter, "service busy");
        }
        return emitter;
    }

    ResponseEntity<?> nonStreamChat(Map<String, Object> body, String model, UUID userId) {
        Set<Long> excludedChannels = new HashSet<>();
        Set<Long> excludedKeys = new HashSet<>();

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            ChannelSelection selection;
            try {
                selection = channelRouter.select(model, excludedChannels, excludedKeys);
            } catch (NoChannelException e) {
                return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "No available channel for model: " + model)));
            }

            Long chId = selection.channel() == null ? null : selection.channel().getId();
            Long kId = selection.key() == null ? null : selection.key().getId();

            try {
                ProviderAdapter adapter = adapterFactory.get(selection.channel().getType());
                String apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
                ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());

                Map<String, Object> req = new HashMap<>(body);
                req.put("stream", false);
                if (selection.actualModel() != null) {
                    req.put("model", selection.actualModel());
                }

                HttpRequest upstreamReq = adapter.buildChatRequest(req, selection.channel(), apiKey, false);
                HttpResponse<String> resp = httpClient.send(upstreamReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    try {
                        UsageSnapshot usage = extractUsage(resp.body());
                        gatewayQuotaService.persistUsageAndDeductQuota(userId, chId, model,
                                usage.promptTokens(), usage.completionTokens(), usage.totalTokens(),
                                UUID.randomUUID().toString());
                    } catch (Exception e) {
                        return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "usage persistence failed")));
                    }
                    channelMonitor.recordSuccess(chId, kId);
                    return ResponseEntity.status(resp.statusCode()).contentType(MediaType.APPLICATION_JSON).body(resp.body());
                }

                channelMonitor.recordFailure(chId, kId, "http_error", resp.statusCode(), "status=" + resp.statusCode());
                excludeForError(resp.statusCode(), chId, kId, excludedChannels, excludedKeys);
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    return ResponseEntity.status(resp.statusCode()).contentType(MediaType.APPLICATION_JSON).body(resp.body());
                }
            } catch (NoChannelException e) {
                return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "No available channel for model: " + model)));
            } catch (Exception e) {
                if (chId != null) {
                    excludedChannels.add(chId);
                }
                if (kId != null) {
                    excludedKeys.add(kId);
                }
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    return ResponseEntity.status(502).body(Map.of("error", Map.of("message", e.getMessage() == null ? "upstream error" : e.getMessage())));
                }
            }
        }
        return ResponseEntity.status(502).body(Map.of("error", Map.of("message", "all retry attempts exhausted")));
    }

    UsageSnapshot extractUsage(String responseBody) {
        try {
            var root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            var usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                throw new IllegalStateException("usage missing");
            }
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(0);
            if (totalTokens <= 0) {
                totalTokens = promptTokens + completionTokens;
            }
            if (totalTokens <= 0) {
                throw new IllegalStateException("usage missing");
            }
            return new UsageSnapshot(promptTokens, completionTokens, totalTokens);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("usage parse failed", e);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(objectMapper.writeValueAsString(
                    Map.of("error", Map.of("message", message == null ? "unknown error" : message))
            ));
        } catch (Exception e) {
            log.warn("Failed to send gateway SSE error event: {}", message, e);
        }
        emitter.complete();
    }

    private static void excludeForError(int statusCode, Long chId, Long kId, Set<Long> exCh, Set<Long> exKey) {
        if (statusCode == 401 || statusCode == 403 || statusCode == 429) {
            if (kId != null) {
                exKey.add(kId);
            }
        } else if (chId != null) {
            exCh.add(chId);
        }
    }

    record UsageSnapshot(int promptTokens, int completionTokens, int totalTokens) {
    }
}
