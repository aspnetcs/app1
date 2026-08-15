package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.ai.extension.FunctionCallingService;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import com.webchat.platformapi.trace.TraceService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.ws.WsSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
public class AiChatService implements ChatStreamStarter {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final AdapterFactory adapterFactory;
    private final ChannelRouter channelRouter;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final ChannelMonitor channelMonitor;
    private final WsSessionRegistry ws;
    private final AuditService auditService;
    private final AiUsageService usageService;
    private final UserRepository userRepo;
    private final TransactionTemplate transactionTemplate;
    private final boolean fallbackEnabled;
    private final FunctionCallingService functionCallingService;
    private final ChatMessagePreparer chatMessagePreparer;
    private final ChatCompletionService chatCompletionService;
    private final ChatStreamProcessor chatStreamProcessor;
    private final RolePolicyService rolePolicyService;
    private final TraceService traceService;
    private final ChatStreamContextRegistry streamContextRegistry;
    private final CreditsPolicyEvaluator creditsPolicyEvaluator;
    private final CreditsRuntimeService creditsRuntimeService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ExecutorService executor;
    private final Semaphore concurrency;

    @jakarta.annotation.PreDestroy
    void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    public AiChatService(
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            WsSessionRegistry ws,
            AuditService auditService,
            AiUsageService usageService,
            UserRepository userRepo,
            FunctionCallingService functionCallingService,
            RolePolicyService rolePolicyService,
            PlatformTransactionManager transactionManager,
            @Nullable TraceService traceService,
            ChatStreamContextRegistry streamContextRegistry,
            @Nullable CreditsPolicyEvaluator creditsPolicyEvaluator,
            @Nullable CreditsRuntimeService creditsRuntimeService,
            @Value("${ai.chat.max-concurrent:8}") int maxConcurrent,
            @Value("${platform.channel-fallback-enabled:true}") boolean fallbackEnabled
    ) {
        this.adapterFactory = adapterFactory;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.channelMonitor = channelMonitor;
        this.ws = ws;
        this.auditService = auditService;
        this.usageService = usageService;
        this.userRepo = userRepo;
        this.functionCallingService = functionCallingService;
        this.rolePolicyService = rolePolicyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.fallbackEnabled = fallbackEnabled;
        this.traceService = traceService == null ? TraceService.noop() : traceService;
        this.streamContextRegistry = streamContextRegistry;
        this.creditsPolicyEvaluator = creditsPolicyEvaluator;
        this.creditsRuntimeService = creditsRuntimeService;
        int threads = Math.max(1, maxConcurrent);
        this.executor = Executors.newFixedThreadPool(threads);
        this.concurrency = new Semaphore(threads);
        this.chatMessagePreparer = new ChatMessagePreparer();
        this.chatCompletionService = new ChatCompletionService(executor, concurrency);
        this.chatStreamProcessor = new ChatStreamProcessor(
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                ws,
                functionCallingService,
                fallbackEnabled,
                chatMessagePreparer,
                this.traceService,
                this.streamContextRegistry,
                httpClient
        );
    }

    @Override
    public boolean startStream(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String ip, String userAgent) {
        return chatCompletionService.startStream(userId, requestId, traceId, requestBody, ip, userAgent, this::stream);
    }

    @Override
    public boolean startStreams(UUID userId, List<ChatStreamTask> tasks, String ip, String userAgent) {
        return chatCompletionService.startStreams(userId, tasks, ip, userAgent, this::stream);
    }

    private void stream(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String ip, String userAgent) {
        chatStreamProcessor.stream(this, userId, requestId, traceId, requestBody, ip, userAgent);
    }

    void audit(UUID userId, String requestId, String model, Long channelId, String channelType, Long keyId,
                       String status, String error, Instant startedAt, String ip, String userAgent,
                       int promptTokens, int completionTokens, int totalTokens) {
        try {
            Map<String, Object> detail = new HashMap<>();
            detail.put("requestId", requestId);
            detail.put("model", model == null ? "" : model);
            detail.put("channelId", channelId == null ? "" : String.valueOf(channelId));
            detail.put("channelType", channelType == null ? "" : channelType);
            detail.put("keyId", keyId == null ? "" : String.valueOf(keyId));
            detail.put("status", status == null ? "" : status);
            detail.put("error", error == null ? "" : error);
            detail.put("startedAt", startedAt == null ? "" : startedAt.toString());
            detail.put("endedAt", Instant.now().toString());
            detail.put("promptTokens", promptTokens);
            detail.put("completionTokens", completionTokens);
            detail.put("totalTokens", totalTokens);
            auditService.log(userId, "ai.chat.stream", detail, ip, userAgent);
        } catch (Exception e) {
            log.warn("[ai] audit failed: userId={}, requestId={}, error={}", userId, requestId, e.toString());
        }
    }

    void monitorSuccess(Long channelId, Long keyId) {
        if (channelMonitor == null) return;
        try {
            channelMonitor.recordSuccess(channelId, keyId);
        } catch (Exception e) {
            log.debug("[ai] monitorSuccess failed: channelId={}, keyId={}, error={}", channelId, keyId, e.toString());
        }
    }

    /**
     * Increment daily chat usage counter after a successful chat stream.
     */
    void incrementDailyUsage(UUID userId) {
        if (rolePolicyService != null) {
            rolePolicyService.incrementDailyUsage(userId);
        }
    }

    void monitorFailure(Long channelId, Long keyId, String code, Integer httpStatus, String error) {
        if (channelMonitor == null) return;
        try {
            channelMonitor.recordFailure(channelId, keyId, code, httpStatus, error);
        } catch (Exception e) {
            log.debug("[ai] monitorFailure failed: channelId={}, keyId={}, code={}, error={}", channelId, keyId, code, e.toString());
        }
    }

    // ============ Feature #5: Token quota check ============

    Long reserveStreamingQuota(UUID userId, String requestId, String traceId, Map<String, Object> requestBody) {
        try {
            UserEntity user = userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
            if (user == null) {
                ws.sendToUser(userId, "chat.error",
                        ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody, "User not found"));
                return null;
            }
            long quota = user.getTokenQuota();
            if (quota <= 0) {
                return 0L;
            }
            long remaining = quota - user.getTokenUsed();
            if (remaining <= 0) {
                ws.sendToUser(userId, "chat.error",
                        ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                                "Token quota exhausted; contact administrator"));
                return null;
            }
            Integer updated = transactionTemplate.execute(status -> userRepo.incrementTokenUsedIfWithinQuota(userId, remaining));
            if (updated == null || updated <= 0) {
                ws.sendToUser(userId, "chat.error",
                        ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                                "Token quota exhausted; contact administrator"));
                return null;
            }
            applyStreamingQuotaLimit(requestBody, remaining);
            return remaining;
        } catch (Exception e) {
            log.warn("[ai] quota reserve failed (fail-closed): userId={}, error={}", userId, e.toString());
            ws.sendToUser(userId, "chat.error",
                    ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                            "Quota lookup failed; please try again later"));
            return null;
        }
    }

    void releaseReservedQuota(UUID userId, long reservedTokens) {
        if (reservedTokens <= 0) {
            return;
        }
        Integer updated = transactionTemplate.execute(status -> userRepo.decrementTokenUsed(userId, reservedTokens));
        if (updated == null || updated <= 0) {
            throw new IllegalStateException("quota refund skipped");
        }
    }

    void logUsageAndFinalizeReservedQuota(UUID userId, long reservedTokens, Long channelId, String channelType, String model,
                                          int promptTokens, int completionTokens, int totalTokens,
                                          long latencyMs, boolean success, String requestId, boolean usageKnown) {
        transactionTemplate.executeWithoutResult(status -> {
            usageService.logUsageStrict(userId, channelId, channelType, model,
                    promptTokens, completionTokens, latencyMs, success, requestId);

            if (!usageKnown || reservedTokens <= 0) {
                return;
            }

            long finalTotal = Math.max(0L, totalTokens);
            long refund = reservedTokens - Math.min(reservedTokens, finalTotal);
            if (refund <= 0) {
                return;
            }

            int updated = userRepo.decrementTokenUsed(userId, refund);
            if (updated <= 0) {
                throw new IllegalStateException("quota refund skipped");
            }
        });
    }

    void finalizeReservedQuotaAfterStream(UUID userId, long reservedTokens, Long channelId, String channelType, String model,
                                          int promptTokens, int completionTokens, int totalTokens,
                                          long latencyMs, boolean success, String requestId,
                                          boolean usageKnown, boolean sentAnyDelta) {
        try {
            logUsageAndFinalizeReservedQuota(userId, reservedTokens, channelId, channelType, model,
                    promptTokens, completionTokens, totalTokens, latencyMs, success, requestId, usageKnown);
        } catch (RuntimeException e) {
            if (sentAnyDelta && usageKnown) {
                try {
                    protectPartialStreamReservation(userId, reservedTokens, totalTokens);
                } catch (RuntimeException correctionFailure) {
                    log.warn("[ai] protect partial stream reservation failed: userId={}, requestId={}, error={}",
                            userId, requestId, correctionFailure.toString());
                    e.addSuppressed(correctionFailure);
                }
            }
            throw e;
        }
    }

    void protectReservedQuotaOnStreamFailure(UUID userId, long reservedTokens,
                                             int promptTokens, int completionTokens, int totalTokens,
                                             boolean sentAnyDelta) {
        if (!sentAnyDelta || reservedTokens <= 0) {
            return;
        }
        // Safety-first: once partial body was emitted, an interrupted stream keeps the reservation.
    }

    void protectPartialStreamReservation(UUID userId, long reservedTokens, int totalTokens) {
        if (reservedTokens <= 0) {
            return;
        }

        long settledTotal = Math.max(0L, totalTokens);
        long refund = reservedTokens - Math.min(reservedTokens, settledTotal);
        if (refund <= 0) {
            return;
        }

        releaseReservedQuota(userId, refund);
    }

    void sendDeltaOrThrow(UUID userId, String requestId, String traceId, String delta) {
        sendDeltaOrThrow(userId, requestId, traceId, null, delta);
    }

    void sendDeltaOrThrow(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String delta) {
        int delivered = ws.sendToUser(userId, "chat.delta",
                ChatMessageBlockEnvelopeFactory.deltaPayload(requestId, traceId, requestBody, delta));
        if (delivered <= 0) {
            throw new IllegalStateException("chat delta delivery failed");
        }
    }

    private static void applyStreamingQuotaLimit(Map<String, Object> requestBody, long reservedTokens) {
        if (requestBody == null || reservedTokens <= 0) {
            return;
        }
        int maxAllowed = reservedTokens > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) reservedTokens;
        Integer maxCompletionTokens = readPositiveInt(requestBody.get("max_completion_tokens"));
        if (maxCompletionTokens != null) {
            requestBody.put("max_completion_tokens", Math.min(maxCompletionTokens, maxAllowed));
            return;
        }
        Integer maxTokens = readPositiveInt(requestBody.get("max_tokens"));
        if (maxTokens != null) {
            requestBody.put("max_tokens", Math.min(maxTokens, maxAllowed));
            return;
        }
        requestBody.put("max_tokens", maxAllowed);
    }

    private static Integer readPositiveInt(Object value) {
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text) {
            try {
                int parsed = Integer.parseInt(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
    // ============ Feature #7: System prompt injection ============

    // ============ Feature #3 + #5: Log usage and deduct quota ============

    void logUsageAndDeductQuota(UUID userId, Long channelId, String channelType, String model,
                                        int promptTokens, int completionTokens, int totalTokens,
                                        long latencyMs, boolean success, String requestId) {
        persistUsageAndDeductQuota(userId, channelId, channelType, model,
                promptTokens, completionTokens, totalTokens, latencyMs, success, requestId);
    }

    void persistUsageAndDeductQuota(UUID userId, Long channelId, String channelType, String model,
                                    int promptTokens, int completionTokens, int totalTokens,
                                    long latencyMs, boolean success, String requestId) {
        transactionTemplate.executeWithoutResult(status -> {
            usageService.logUsageStrict(userId, channelId, channelType, model,
                    promptTokens, completionTokens, latencyMs, success, requestId);

            if (totalTokens <= 0) {
                return;
            }

            int updated = userRepo.incrementTokenUsedIfWithinQuota(userId, totalTokens);
            if (updated <= 0) {
                throw new IllegalStateException("quota deduct skipped");
            }
        });
    }

    // ============ Credits system integration ============

    /**
     * Evaluate credits policy before starting a stream. Returns null if credits system is disabled or free mode.
     */
    CreditsPolicyEvaluator.PolicyDecision evaluateCreditsPolicy(UUID userId, String role, String model) {
        if (creditsPolicyEvaluator == null) {
            return null;
        }
        return creditsPolicyEvaluator.evaluate(userId, null, role, model);
    }

    /**
     * Reserve credits before streaming. Returns snapshot ID or null if not needed.
     */
    Long reserveCredits(UUID userId, String role, String model,
                        CreditsPolicyEvaluator.PolicyDecision decision, String requestId) {
        if (creditsRuntimeService == null || decision == null || !decision.creditsRequired()) {
            return null;
        }
        return creditsRuntimeService.reserve(userId, null, role, model,
                decision.modelMeta(), decision.account(), requestId);
    }

    /**
     * Settle credits after successful streaming.
     */
    void settleCredits(Long snapshotId, int promptTokens, int completionTokens) {
        if (creditsRuntimeService == null || snapshotId == null) {
            return;
        }
        creditsRuntimeService.settle(snapshotId, 1, 1, promptTokens, completionTokens);
    }

    /**
     * Full refund when no content was sent.
     */
    void refundCredits(Long snapshotId) {
        if (creditsRuntimeService == null || snapshotId == null) {
            return;
        }
        creditsRuntimeService.refund(snapshotId);
    }

    /**
     * Partial settle when content was partially sent.
     */
    void partialSettleCredits(Long snapshotId, int promptTokens, int completionTokens) {
        if (creditsRuntimeService == null || snapshotId == null) {
            return;
        }
        creditsRuntimeService.partialSettle(snapshotId, promptTokens, completionTokens);
    }

    CreditsPolicyEvaluator getCreditsPolicyEvaluator() {
        return creditsPolicyEvaluator;
    }

    CreditsRuntimeService getCreditsRuntimeService() {
        return creditsRuntimeService;
    }
}
