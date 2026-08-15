package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.extension.FunctionCallingService;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.trace.TraceService;
import com.webchat.platformapi.ws.WsSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ChatStreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamProcessor.class);
    private static final int MAX_SELECTION_ATTEMPTS = 5;

    private final AdapterFactory adapterFactory;
    private final ChannelRouter channelRouter;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final WsSessionRegistry ws;
    private final FunctionCallingService functionCallingService;
    private final boolean fallbackEnabled;
    private final ChatMessagePreparer messagePreparer;
    private final TraceService traceService;
    private final ChatStreamContextRegistry streamContextRegistry;
    private final HttpClient httpClient;

    private static String ensureTraceId(String raw) {
        if (raw != null) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static int messageCount(Map<String, Object> req) {
        if (req == null) {
            return 0;
        }
        Object messages = req.get("messages");
        if (messages instanceof java.util.Collection<?> c) {
            return c.size();
        }
        return 0;
    }

    ChatStreamProcessor(
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            WsSessionRegistry ws,
            FunctionCallingService functionCallingService,
            boolean fallbackEnabled,
            ChatMessagePreparer messagePreparer,
            TraceService traceService,
            ChatStreamContextRegistry streamContextRegistry,
            HttpClient httpClient
    ) {
        this.adapterFactory = adapterFactory;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.ws = ws;
        this.functionCallingService = functionCallingService;
        this.fallbackEnabled = fallbackEnabled;
        this.messagePreparer = messagePreparer;
        this.traceService = traceService == null ? TraceService.noop() : traceService;
        this.streamContextRegistry = streamContextRegistry;
        this.httpClient = httpClient;
    }

    void stream(AiChatService owner, UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String ip, String userAgent) {
        Instant startedAt = Instant.now();
        Map<String, Object> streamRequestBody = requestBody == null ? new HashMap<>() : new HashMap<>(requestBody);
        String normalizedTraceId = ensureTraceId(traceId);

        String model = null;
        Object m = streamRequestBody.get("model");
        if (m != null) {
            String t = String.valueOf(m).trim();
            if (!t.isEmpty()) {
                model = t;
            }
        }
        if (streamContextRegistry != null) {
            streamContextRegistry.registerStart(userId, requestId, normalizedTraceId, model == null ? "" : model);
        }

        traceService.recordSpan(
                userId,
                normalizedTraceId,
                requestId,
                null,
                "chat.completions.ws",
                startedAt,
                null,
                "started",
                Map.of(
                        "model", model == null ? "" : model,
                        "messageCount", messageCount(streamRequestBody)
                )
        );

        if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                null, null, null, startedAt, ip, userAgent, 0, 0, 0)) {
            return;
        }

        Long reservedTokens = owner.reserveStreamingQuota(userId, requestId, normalizedTraceId, streamRequestBody);
        if (reservedTokens == null) {
            return;
        }

        // Credits system: evaluate policy and reserve credits
        Long creditsSnapshotId = null;
        try {
            // Resolve user role from request body or default
            String role = streamRequestBody.containsKey("__userRole")
                    ? String.valueOf(streamRequestBody.get("__userRole")) : "user";
            var creditsDecision = owner.evaluateCreditsPolicy(userId, role, model);
            if (creditsDecision != null && !creditsDecision.allowed()) {
                owner.releaseReservedQuota(userId, reservedTokens > 0 ? reservedTokens : 0);
                sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                        resolveCreditsErrorMessage(creditsDecision.denialReason()));
                return;
            }
            if (creditsDecision != null && creditsDecision.creditsRequired()) {
                creditsSnapshotId = owner.reserveCredits(userId, role, model, creditsDecision, requestId);
                if (creditsSnapshotId == null) {
                    owner.releaseReservedQuota(userId, reservedTokens > 0 ? reservedTokens : 0);
                    sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                            resolveCreditsErrorMessage("credits_reserve_failed"));
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("[ai] credits evaluation failed (fail-closed): userId={}, error={}", userId, e.toString());
            if (reservedTokens > 0) {
                try {
                    owner.releaseReservedQuota(userId, reservedTokens);
                } catch (Exception releaseError) {
                    log.warn("[ai] release reserved quota after credits failure failed: userId={}, requestId={}, error={}",
                            userId, requestId, releaseError.toString());
                }
            }
            sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                    resolveCreditsErrorMessage("credits_policy_unavailable"));
            return;
        }

        boolean reservationFinalized = false;
        boolean sentAnyDelta = false;
        String endStatus = "done";
        String endError = "";
        Long channelId = null;
        Long keyId = null;
        String channelType = null;
        String lastFailureStatus = "";
        String lastFailureError = "";
        Integer lastFailureHttpStatus = null;

        Set<Long> excludedChannels = new HashSet<>();
        Set<Long> excludedKeys = new HashSet<>();
        Set<Long> attemptedFallbackChannels = new HashSet<>();
        Long preferredFallbackChannelId = null;

        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        try {
            for (int attempt = 1; attempt <= MAX_SELECTION_ATTEMPTS; attempt++) {
                endStatus = "";
                endError = "";

                ChannelSelection selection;
                try {
                    if (preferredFallbackChannelId != null) {
                        selection = channelRouter.selectSpecificChannel(preferredFallbackChannelId, model == null ? "" : model, excludedKeys);
                    } else {
                        selection = channelRouter.select(model == null ? "" : model, excludedChannels, excludedKeys);
                    }
                } catch (NoChannelException e) {
                    if (preferredFallbackChannelId != null) {
                        excludedChannels.add(preferredFallbackChannelId);
                        preferredFallbackChannelId = null;
                        continue;
                    }
                    endStatus = lastFailureStatus == null || lastFailureStatus.isBlank() ? "no_channel" : lastFailureStatus;
                    endError = lastFailureError == null || lastFailureError.isBlank() ? e.getMessage() : lastFailureError;
                    sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                            resolveUserFacingErrorMessage(endStatus, lastFailureHttpStatus, endError));
                    traceService.recordSpan(
                            userId,
                            normalizedTraceId,
                            requestId,
                            null,
                            "chat.completions.ws.end",
                            startedAt,
                            Instant.now(),
                            endStatus,
                            Map.of("error", endError == null ? "" : endError)
                    );
                    owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                            0, 0, 0);
                    return;
                }

                channelId = selection.channel() == null ? null : selection.channel().getId();
                channelType = selection.channel() == null ? null : selection.channel().getType();
                keyId = selection.key() == null ? null : selection.key().getId();
                preferredFallbackChannelId = null;

                if (streamContextRegistry != null) {
                    streamContextRegistry.updateChannel(userId, requestId, channelId, channelType);
                }

                ProviderAdapter adapter;
                try {
                    adapter = adapterFactory.get(channelType);
                } catch (Exception e) {
                    endStatus = "unsupported_adapter";
                    endError = e.getMessage();
                    lastFailureStatus = endStatus;
                    lastFailureError = endError;
                    lastFailureHttpStatus = null;
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    owner.monitorFailure(channelId, keyId, endStatus, null, endError);
                    if (channelId != null) {
                        excludedChannels.add(channelId);
                    }
                    continue;
                }

                String apiKey;
                try {
                    apiKey = cryptoService.decrypt(selection.key().getApiKeyEncrypted());
                } catch (Exception e) {
                    endStatus = "decrypt_failed";
                    endError = e.getMessage() == null ? "decrypt_failed" : e.getMessage();
                    lastFailureStatus = endStatus;
                    lastFailureError = endError;
                    lastFailureHttpStatus = null;
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    owner.monitorFailure(channelId, keyId, endStatus, null, endError);
                    if (keyId != null) {
                        excludedKeys.add(keyId);
                    }
                    continue;
                }

                try {
                    ssrfGuard.assertAllowedBaseUrl(selection.channel().getBaseUrl());
                } catch (SsrfGuard.SsrfException e) {
                    endStatus = "ssrf_blocked";
                    endError = e.getMessage();
                    lastFailureStatus = endStatus;
                    lastFailureError = endError;
                    lastFailureHttpStatus = null;
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    owner.monitorFailure(channelId, keyId, endStatus, null, endError);
                    if (channelId != null) {
                        excludedChannels.add(channelId);
                    }
                    continue;
                }

                Map<String, Object> req = new HashMap<>(streamRequestBody);
                req.put("stream", true);
                if (selection.actualModel() != null && !selection.actualModel().isBlank()) {
                    req.put("model", selection.actualModel());
                }

                messagePreparer.injectSystemPrompt(req, selection.channel());

                HttpRequest upstreamReq;
                if (functionCallingService.supports(req, channelType)) {
                    Instant toolStartAt = Instant.now();
                    try {
                        if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                                channelId, channelType, keyId, startedAt, ip, userAgent,
                                promptTokens, completionTokens, totalTokens)) {
                            return;
                        }
                        FunctionCallingService.FunctionCallingOutcome outcome =
                                functionCallingService.execute(selection.channel(), apiKey, req);
                        traceService.recordSpan(
                                userId,
                                normalizedTraceId,
                                requestId,
                                null,
                                "chat.completions.ws.tool",
                                toolStartAt,
                                Instant.now(),
                                "ok",
                                Map.of(
                                        "channelId", channelId == null ? "" : String.valueOf(channelId),
                                        "keyId", keyId == null ? "" : String.valueOf(keyId)
                                )
                        );
                        int finalTotal = outcome.totalTokens() > 0 ? outcome.totalTokens() : (outcome.promptTokens() + outcome.completionTokens());
                        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
                        String finalContent = outcome.content() == null ? "" : outcome.content();
                        if (!finalContent.isEmpty()) {
                            if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                                    channelId, channelType, keyId, startedAt, ip, userAgent,
                                    promptTokens, completionTokens, totalTokens)) {
                                return;
                            }
                            owner.sendDeltaOrThrow(userId, requestId, normalizedTraceId, streamRequestBody, finalContent);
                            sentAnyDelta = true;
                        }
                        try {
                            if (reservedTokens > 0) {
                                owner.finalizeReservedQuotaAfterStream(userId, reservedTokens, channelId, channelType, model,
                                        outcome.promptTokens(), outcome.completionTokens(), finalTotal,
                                        latencyMs, true, requestId, finalTotal > 0, sentAnyDelta);
                            } else {
                                owner.logUsageAndDeductQuota(userId, channelId, channelType, model,
                                        outcome.promptTokens(), outcome.completionTokens(), finalTotal,
                                        latencyMs, true, requestId);
                            }
                            owner.settleCredits(creditsSnapshotId, outcome.promptTokens(), outcome.completionTokens());
                            reservationFinalized = true;
                        } catch (RuntimeException e) {
                            endStatus = "usage_quota_failed";
                            endError = e.getMessage() == null ? "usage_quota_failed" : e.getMessage();
                            sendError(userId, requestId, normalizedTraceId, streamRequestBody, "usage persistence failed");
                            traceService.recordSpan(
                                    userId,
                                    normalizedTraceId,
                                    requestId,
                                    null,
                                    "chat.completions.ws.end",
                                    startedAt,
                                    Instant.now(),
                                    endStatus,
                                    Map.of("error", endError)
                            );
                            owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                    outcome.promptTokens(), outcome.completionTokens(), finalTotal);
                            return;
                        }
                        if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                                channelId, channelType, keyId, startedAt, ip, userAgent,
                                outcome.promptTokens(), outcome.completionTokens(), finalTotal)) {
                            return;
                        }
                        sendDone(userId, requestId, normalizedTraceId, streamRequestBody, Map.of("toolTrace", outcome.toolTrace()));
                        endStatus = "done";
                        endError = "";
                        traceService.recordSpan(
                                userId,
                                normalizedTraceId,
                                requestId,
                                null,
                                "chat.completions.ws.end",
                                startedAt,
                                Instant.now(),
                                endStatus,
                                Map.of(
                                        "promptTokens", outcome.promptTokens(),
                                        "completionTokens", outcome.completionTokens(),
                                        "totalTokens", finalTotal
                                )
                        );
                        owner.monitorSuccess(channelId, keyId);
                        owner.incrementDailyUsage(userId);
                        owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                outcome.promptTokens(), outcome.completionTokens(), finalTotal);
                        return;
                    } catch (FunctionCallingService.FunctionCallingException e) {
                        endStatus = "function_calling_failed";
                        endError = e.getMessage() == null ? "function_calling_failed" : e.getMessage();
                        traceService.recordSpan(
                                userId,
                                normalizedTraceId,
                                requestId,
                                null,
                                "chat.completions.ws.tool",
                                toolStartAt,
                                Instant.now(),
                                "error",
                                Map.of("error", endError)
                        );
                        lastFailureStatus = endStatus;
                        lastFailureError = endError;
                        lastFailureHttpStatus = null;
                        logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                        if (e.isChannelFailure()) {
                            owner.monitorFailure(channelId, keyId, endStatus, null, endError);
                            if (channelId != null) {
                                excludedChannels.add(channelId);
                            }
                            if (keyId != null) {
                                excludedKeys.add(keyId);
                            }
                            continue;
                        }
                        sendError(userId, requestId, normalizedTraceId, streamRequestBody, endError);
                        traceService.recordSpan(
                                userId,
                                normalizedTraceId,
                                requestId,
                                null,
                                "chat.completions.ws.end",
                                startedAt,
                                Instant.now(),
                                endStatus,
                                Map.of("error", endError)
                        );
                        owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                0, 0, 0);
                        return;
                    } catch (Exception e) {
                        if (endStatus == null || endStatus.isBlank()) {
                            endStatus = "exception";
                        }
                        if (endError == null || endError.isBlank()) {
                            endError = e.getMessage() == null ? "stream_failed" : e.getMessage();
                        }
                        lastFailureStatus = endStatus;
                        lastFailureError = endError;
                        lastFailureHttpStatus = null;
                        logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                        owner.monitorFailure(channelId, keyId, endStatus, null, endError);
                        if (!sentAnyDelta && attempt < MAX_SELECTION_ATTEMPTS) {
                            if (channelId != null) {
                                excludedChannels.add(channelId);
                            }
                            if (keyId != null) {
                                excludedKeys.add(keyId);
                            }
                            continue;
                        }

                        if (reservedTokens > 0) {
                            try {
                                owner.protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                        promptTokens, completionTokens, totalTokens, sentAnyDelta);
                            } catch (RuntimeException correctionFailure) {
                                log.warn("[ai] protect stream failure reservation failed: userId={}, requestId={}, error={}",
                                        userId, requestId, correctionFailure.toString());
                                e.addSuppressed(correctionFailure);
                            }
                        }

                        sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                                "Service temporarily unavailable; please try again later");
                        traceService.recordSpan(
                                userId,
                                normalizedTraceId,
                                requestId,
                                null,
                                "chat.completions.ws.end",
                                startedAt,
                                Instant.now(),
                                endStatus,
                                Map.of("error", endError)
                        );
                        owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                promptTokens, completionTokens, totalTokens);
                        return;
                    }
                }

                if (req.get("toolNames") instanceof List<?> requestedTools && !requestedTools.isEmpty()) {
                    sendNotice(userId, requestId, normalizedTraceId, streamRequestBody,
                            "Tools are not supported for this model; using standard chat");
                }
                req.remove("toolNames");
                try {
                    upstreamReq = adapter.buildChatRequest(req, selection.channel(), apiKey, true);
                } catch (Exception e) {
                    endStatus = "build_request_failed";
                    endError = e.getMessage() == null ? "build_request_failed" : e.getMessage();
                    lastFailureStatus = endStatus;
                    lastFailureError = endError;
                    lastFailureHttpStatus = null;
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    owner.monitorFailure(channelId, keyId, endStatus, null, endError);
                    if (channelId != null) {
                        excludedChannels.add(channelId);
                    }
                    if (keyId != null) {
                        excludedKeys.add(keyId);
                    }
                    continue;
                }

                Instant upstreamStartAt = Instant.now();
                String upstreamHost = "";
                try {
                    if (upstreamReq.uri() != null && upstreamReq.uri().getHost() != null) {
                        upstreamHost = upstreamReq.uri().getHost();
                    }
                } catch (Exception ignored) {
                    upstreamHost = "";
                }

                try {
                    HttpResponse<InputStream> resp = httpClient.send(upstreamReq, HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream body = resp.body()) {
                        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                            endStatus = "http_error";
                            endError = "status=" + resp.statusCode();
                            lastFailureStatus = endStatus;
                            lastFailureError = endError;
                            lastFailureHttpStatus = resp.statusCode();
                            logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, resp.statusCode());
                             owner.monitorFailure(channelId, keyId, endStatus, resp.statusCode(), endError);
                             traceService.recordSpan(
                                     userId,
                                     normalizedTraceId,
                                     requestId,
                                     null,
                                     "chat.completions.ws.upstream",
                                     upstreamStartAt,
                                     Instant.now(),
                                     endStatus,
                                     Map.of(
                                             "httpStatus", resp.statusCode(),
                                             "channelId", channelId == null ? "" : String.valueOf(channelId),
                                             "keyId", keyId == null ? "" : String.valueOf(keyId),
                                             "adapter", adapter == null ? "" : adapter.type(),
                                             "host", upstreamHost
                                     )
                             );
                              if (!sentAnyDelta) {
                                   Long fallbackChannelId = selection.channel() == null ? null : selection.channel().getFallbackChannelId();
                                   if (fallbackEnabled
                                           && fallbackChannelId != null
                                          && !Objects.equals(fallbackChannelId, channelId)
                                          && !excludedChannels.contains(fallbackChannelId)
                                          && !attemptedFallbackChannels.contains(fallbackChannelId)) {
                                      attemptedFallbackChannels.add(fallbackChannelId);
                                      if (channelId != null) {
                                          excludedChannels.add(channelId);
                                      }
                                      preferredFallbackChannelId = fallbackChannelId;
                                       sendNotice(userId, requestId, normalizedTraceId, streamRequestBody, "Switched to fallback channel");
                                      continue;
                                  }

                                 int status = resp.statusCode();
                                 if (status >= 500 && status < 600) {
                                     // For transient upstream 5xx, retry the same channel once.
                                     if (attempt == 1) {
                                         continue;
                                     }
                                     // If there are multiple channels configured, exclude the failing one to allow
                                     // rerouting; otherwise fail fast (single-channel setup).
                                     boolean hasAlternativeChannel = false;
                                     try {
                                         List<?> routable = channelRouter.listRoutableChannels();
                                         hasAlternativeChannel = routable != null && routable.size() > 1;
                                     } catch (Exception ignored) {
                                         hasAlternativeChannel = false;
                                     }
                                     if (hasAlternativeChannel && channelId != null) {
                                         excludedChannels.add(channelId);
                                         continue;
                                     }
                                     // No alternative channel: don't burn the remaining attempts on the same failing upstream.
                                     break;
                                 }

                                 excludeForHttpError(status, channelId, keyId, excludedChannels, excludedKeys);
                                 continue;
                               }
                               sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                                       resolveUserFacingErrorMessage(endStatus, lastFailureHttpStatus, endError));
                             traceService.recordSpan(
                                     userId,
                                     normalizedTraceId,
                                     requestId,
                                     null,
                                     "chat.completions.ws.end",
                                     startedAt,
                                     Instant.now(),
                                     endStatus,
                                     Map.of("error", endError == null ? "" : endError)
                             );
                             owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                     promptTokens, completionTokens, totalTokens);
                             return;
                         }

                        try (BufferedReader br = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                            String line;
                            boolean parseErrorLogged = false;
                            while ((line = br.readLine()) != null) {
                                if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                                        channelId, channelType, keyId, startedAt, ip, userAgent,
                                        promptTokens, completionTokens, totalTokens)) {
                                    return;
                                }
                                StreamChunk chunk;
                                try {
                                    chunk = adapter.parseStreamLine(line);
                                } catch (Exception e) {
                                    if (!parseErrorLogged) {
                                        parseErrorLogged = true;
                                        log.debug("[ai] parse stream line failed: channelId={}, keyId={}, adapter={}, error={}",
                                                channelId, keyId, adapter == null ? "" : adapter.type(), e.toString());
                                    }
                                    continue;
                                }
                                if (chunk == null) {
                                    continue;
                                }

                                if (chunk.errorMessage() != null && !chunk.errorMessage().isBlank()) {
                                    endStatus = "upstream_error";
                                    endError = chunk.errorMessage();
                                    lastFailureStatus = endStatus;
                                    lastFailureError = endError;
                                    lastFailureHttpStatus = null;
                                    traceService.recordSpan(
                                            userId,
                                            normalizedTraceId,
                                            requestId,
                                            null,
                                            "chat.completions.ws.upstream",
                                            upstreamStartAt,
                                            Instant.now(),
                                            endStatus,
                                            Map.of(
                                                    "channelId", channelId == null ? "" : String.valueOf(channelId),
                                                    "keyId", keyId == null ? "" : String.valueOf(keyId),
                                                    "adapter", adapter == null ? "" : adapter.type(),
                                                    "host", upstreamHost,
                                                    "error", endError
                                            )
                                    );
                                    throw new IllegalStateException("upstream_error");
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

                                if (chunk.delta() != null && !chunk.delta().isEmpty()) {
                                    if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                                            channelId, channelType, keyId, startedAt, ip, userAgent,
                                            promptTokens, completionTokens, totalTokens)) {
                                        return;
                                    }
                                    owner.sendDeltaOrThrow(userId, requestId, normalizedTraceId, streamRequestBody, chunk.delta());
                                    sentAnyDelta = true;
                                }
                                if (chunk.reasoningDelta() != null && !chunk.reasoningDelta().isEmpty()) {
                                    if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                                            channelId, channelType, keyId, startedAt, ip, userAgent,
                                            promptTokens, completionTokens, totalTokens)) {
                                        return;
                                    }
                                    // Keep WS behavior consistent with SSE: emit reasoning deltas separately so
                                    // mini-program clients can render the "thinking" panel instead of appearing
                                    // stuck with an empty reply when models only stream reasoning_content.
                                    sendThinking(userId, requestId, normalizedTraceId, streamRequestBody, chunk.reasoningDelta());
                                 }
                                if (chunk.done()) {
                                    break;
                                }
                            }
                        }
                    }

                    traceService.recordSpan(
                            userId,
                            normalizedTraceId,
                            requestId,
                            null,
                            "chat.completions.ws.upstream",
                            upstreamStartAt,
                            Instant.now(),
                            "ok",
                            Map.of(
                                    "httpStatus", resp.statusCode(),
                                    "channelId", channelId == null ? "" : String.valueOf(channelId),
                                    "keyId", keyId == null ? "" : String.valueOf(keyId),
                                    "adapter", adapter == null ? "" : adapter.type(),
                                    "host", upstreamHost
                            )
                    );

                    long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
                    int finalTotal = totalTokens > 0 ? totalTokens : (promptTokens + completionTokens);
                    try {
                        if (reservedTokens > 0) {
                            owner.finalizeReservedQuotaAfterStream(userId, reservedTokens, channelId, channelType, model,
                                    promptTokens, completionTokens, finalTotal, latencyMs, true, requestId,
                                    finalTotal > 0, sentAnyDelta);
                        } else {
                            owner.logUsageAndDeductQuota(userId, channelId, channelType, model,
                                    promptTokens, completionTokens, finalTotal, latencyMs, true, requestId);
                        }
                        owner.settleCredits(creditsSnapshotId, promptTokens, completionTokens);
                        reservationFinalized = true;
                    } catch (RuntimeException e) {
                        endStatus = "usage_quota_failed";
                        endError = e.getMessage() == null ? "usage_quota_failed" : e.getMessage();
                        sendError(userId, requestId, normalizedTraceId, streamRequestBody, "usage persistence failed");
                        traceService.recordSpan(
                                userId,
                                normalizedTraceId,
                                requestId,
                                null,
                                "chat.completions.ws.end",
                                startedAt,
                                Instant.now(),
                                endStatus,
                                Map.of("error", endError)
                        );
                        owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                promptTokens, completionTokens, finalTotal);
                        return;
                    }

                    if (handleAbortIfRequested(owner, userId, requestId, normalizedTraceId, model, streamRequestBody,
                            channelId, channelType, keyId, startedAt, ip, userAgent,
                            promptTokens, completionTokens, finalTotal)) {
                        return;
                    }
                    sendDone(userId, requestId, normalizedTraceId, streamRequestBody, null);
                    endStatus = "done";
                    endError = "";
                    traceService.recordSpan(
                            userId,
                            normalizedTraceId,
                            requestId,
                            null,
                            "chat.completions.ws.end",
                            startedAt,
                            Instant.now(),
                            endStatus,
                            Map.of(
                                    "promptTokens", promptTokens,
                                    "completionTokens", completionTokens,
                                    "totalTokens", finalTotal
                            )
                    );
                    owner.monitorSuccess(channelId, keyId);
                    owner.incrementDailyUsage(userId);
                    owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                            promptTokens, completionTokens, totalTokens);
                    return;
                } catch (Exception e) {
                    if (endStatus == null || endStatus.isBlank()) {
                        endStatus = "exception";
                    }
                    if (endError == null || endError.isBlank()) {
                        endError = e.getMessage() == null ? "stream_failed" : e.getMessage();
                    }
                    lastFailureStatus = endStatus;
                    lastFailureError = endError;
                    lastFailureHttpStatus = null;
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    owner.monitorFailure(channelId, keyId, endStatus, null, endError);
                    if (!sentAnyDelta && attempt < MAX_SELECTION_ATTEMPTS) {
                        Long fallbackChannelId = selection.channel() == null ? null : selection.channel().getFallbackChannelId();
                        if (fallbackEnabled
                                && fallbackChannelId != null
                                && !Objects.equals(fallbackChannelId, channelId)
                                && !excludedChannels.contains(fallbackChannelId)
                                && !attemptedFallbackChannels.contains(fallbackChannelId)) {
                            attemptedFallbackChannels.add(fallbackChannelId);
                            if (channelId != null) {
                                excludedChannels.add(channelId);
                            }
                            preferredFallbackChannelId = fallbackChannelId;
                            sendNotice(userId, requestId, normalizedTraceId, streamRequestBody, "Switched to fallback channel");
                            continue;
                        }
                        if (channelId != null) {
                            excludedChannels.add(channelId);
                        }
                        if (keyId != null) {
                            excludedKeys.add(keyId);
                        }
                        continue;
                    }

                    if (reservedTokens > 0) {
                        try {
                            owner.protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                    promptTokens, completionTokens, totalTokens, sentAnyDelta);
                        } catch (RuntimeException correctionFailure) {
                            log.warn("[ai] protect stream failure reservation failed: userId={}, requestId={}, error={}",
                                    userId, requestId, correctionFailure.toString());
                            e.addSuppressed(correctionFailure);
                        }
                    }

                    sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                            resolveUserFacingErrorMessage(endStatus, lastFailureHttpStatus, endError));
                    traceService.recordSpan(
                            userId,
                            normalizedTraceId,
                            requestId,
                            null,
                            "chat.completions.ws.end",
                            startedAt,
                            Instant.now(),
                            endStatus,
                            Map.of("error", endError == null ? "" : endError)
                    );
                    owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                            promptTokens, completionTokens, totalTokens);
                    return;
                }
            }
        } finally {
            if (reservedTokens > 0 && !reservationFinalized && !sentAnyDelta) {
                try {
                    owner.releaseReservedQuota(userId, reservedTokens);
                } catch (Exception e) {
                    log.warn("[ai] release reserved quota failed: userId={}, requestId={}, error={}", userId, requestId, e.toString());
                }
            }
            // Credits: refund if not settled and no content was sent
            if (creditsSnapshotId != null && !reservationFinalized && !sentAnyDelta) {
                try {
                    owner.refundCredits(creditsSnapshotId);
                } catch (Exception e) {
                    log.warn("[ai] credits refund failed: userId={}, requestId={}, snapshotId={}, error={}",
                            userId, requestId, creditsSnapshotId, e.toString());
                }
            }
            // Credits: partial settle if content was sent but not finalized
            if (creditsSnapshotId != null && !reservationFinalized && sentAnyDelta) {
                try {
                    owner.partialSettleCredits(creditsSnapshotId, promptTokens, completionTokens);
                } catch (Exception e) {
                    log.warn("[ai] credits partial settle failed: userId={}, requestId={}, snapshotId={}, error={}",
                            userId, requestId, creditsSnapshotId, e.toString());
                }
            }
        }

        endStatus = "exhausted";
        endError = lastFailureError == null || lastFailureError.isBlank() ? "no_upstream_available" : lastFailureError;
        sendError(userId, requestId, normalizedTraceId, streamRequestBody,
                resolveUserFacingErrorMessage(lastFailureStatus, lastFailureHttpStatus, endError));
        traceService.recordSpan(
                userId,
                normalizedTraceId,
                requestId,
                null,
                "chat.completions.ws.end",
                startedAt,
                Instant.now(),
                endStatus,
                Map.of("error", endError == null ? "" : endError)
        );
        owner.audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                promptTokens, completionTokens, totalTokens);
    }

    private boolean handleAbortIfRequested(AiChatService owner,
                                          UUID userId,
                                          String requestId,
                                          String traceId,
                                          String model,
                                          Map<String, Object> requestBody,
                                          Long channelId,
                                          String channelType,
                                          Long keyId,
                                          Instant startedAt,
                                          String ip,
                                          String userAgent,
                                          int promptTokens,
                                          int completionTokens,
                                          int totalTokens) {
        if (streamContextRegistry == null) {
            return false;
        }
        ChatStreamContextRegistry.Snapshot snapshot = streamContextRegistry.get(userId, requestId);
        if (snapshot == null || !snapshot.aborted()) {
            return false;
        }
        String reason = snapshot.abortReason() == null ? "" : snapshot.abortReason().trim();
        if (reason.isEmpty()) {
            reason = "user_cancel";
        }

        boolean shouldSendTerminal = streamContextRegistry.markTerminalSentIfAbsent(userId, requestId);
        if (shouldSendTerminal) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>(
                    ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody, "aborted"));
            payload.put("reason", reason);
            ws.sendToUser(userId, "chat.error", payload);
        }

        traceService.recordSpan(
                userId,
                traceId,
                requestId,
                null,
                "chat.completions.ws.end",
                startedAt,
                Instant.now(),
                "aborted",
                Map.of("reason", reason)
        );
        owner.audit(userId, requestId, model, channelId, channelType, keyId,
                "aborted", reason, startedAt, ip, userAgent, promptTokens, completionTokens, totalTokens);
        return true;
    }

    static String resolveUserFacingErrorMessage(String status, Integer httpStatus, String error) {
        if ("decrypt_failed".equalsIgnoreCase(status)) {
            return "当前渠道密钥配置无效，请联系管理员检查渠道密钥";
        }
        if ("ssrf_blocked".equalsIgnoreCase(status)) {
            return "当前渠道地址被安全策略拦截，请联系管理员检查渠道配置";
        }
        if ("no_channel".equalsIgnoreCase(status) || "exhausted".equalsIgnoreCase(status)) {
            return "当前没有可用渠道，请稍后再试";
        }
        if ("http_error".equalsIgnoreCase(status) && httpStatus != null) {
            if (httpStatus >= 500) {
                return "上游服务异常 (HTTP " + httpStatus + ")，请稍后再试";
            }
            if (httpStatus == 401 || httpStatus == 403) {
                return "当前渠道鉴权失败，请联系管理员更新渠道密钥";
            }
            if (httpStatus == 429) {
                return "当前渠道限流，请稍后再试";
            }
        }
        if ("upstream_error".equalsIgnoreCase(status) && error != null && !error.isBlank()) {
            return "上游渠道返回错误，请稍后再试";
        }
        return "当前渠道暂时不可用，请稍后再试";
    }

    private void excludeForHttpError(int statusCode, Long channelId, Long keyId, Set<Long> excludedChannels, Set<Long> excludedKeys) {
        if (excludedChannels == null || excludedKeys == null) {
            return;
        }
        if (statusCode == 401 || statusCode == 403 || statusCode == 429) {
            if (keyId != null) {
                excludedKeys.add(keyId);
            }
            return;
        }
        if (channelId != null) {
            excludedChannels.add(channelId);
        }
    }

    private void logAttemptFailure(String requestId, int attempt, Long channelId, Long keyId,
                                   String status, String error, Integer httpStatus) {
        log.warn("[ai.stream] attempt failed: requestId={}, attempt={}, channelId={}, keyId={}, status={}, httpStatus={}, error={}",
                requestId, attempt, channelId, keyId, status, httpStatus, error);
    }

    private void sendError(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String message) {
        ws.sendToUser(userId, "chat.error",
                ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody, message));
    }

    private void sendNotice(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String message) {
        ws.sendToUser(userId, "chat.notice",
                ChatMessageBlockEnvelopeFactory.noticePayload(requestId, traceId, requestBody, message));
    }

    private void sendThinking(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String delta) {
        ws.sendToUser(userId, "chat.thinking",
                ChatMessageBlockEnvelopeFactory.thinkingPayload(requestId, traceId, requestBody, delta));
    }

    private void sendDone(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, Map<String, Object> extra) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(
                ChatMessageBlockEnvelopeFactory.donePayload(requestId, traceId, requestBody));
        if (extra != null && !extra.isEmpty()) {
            payload.putAll(extra);
        }
        ws.sendToUser(userId, "chat.done", payload);
    }

    static String resolveCreditsErrorMessage(String reason) {
        if ("credits_insufficient".equals(reason)) {
            return "Credits 余额不足，请联系管理员充值";
        }
        if ("credits_account_not_found".equals(reason)) {
            return "Credits 账户未创建，请联系管理员";
        }
        if ("credits_reserve_failed".equals(reason)) {
            return "Credits 预扣失败，请稍后再试";
        }
        if ("credits_policy_unavailable".equals(reason)) {
            return "Credits policy temporarily unavailable; please try again later";
        }
        if ("model_not_allowed".equals(reason)) {
            return "当前角色无权使用此模型";
        }
        return "Credits 校验失败，请稍后再试";
    }
}
