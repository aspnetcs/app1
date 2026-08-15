package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.extension.FunctionCallingService;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.credits.CreditsErrorSupport;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import com.webchat.platformapi.common.filter.RequestIdFilter;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.trace.TraceService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * web-only optional: SSE streaming compatibility.
 * Primary streaming path is still: POST /api/v1/chat/completions + /ws push.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatSseController {

    private static final Logger log = LoggerFactory.getLogger(ChatSseController.class);
    private static final int MAX_SELECTION_ATTEMPTS = 5;

    private final ObjectMapper objectMapper;
    private final AdapterFactory adapterFactory;
    private final ChannelRouter channelRouter;
    private final AiCryptoService cryptoService;
    private final SsrfGuard ssrfGuard;
    private final ChannelMonitor channelMonitor;
    private final AuditService auditService;
    private final AiUsageService usageService;
    private final UserRepository userRepo;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor;
    private final ChatSseEventService chatSseEventService;
    private final ChatSseRequestSupport chatSseRequestSupport;
    private final ChatAttachmentPreprocessor attachmentPreprocessor;
    private final ChatMcpToolContextService chatMcpToolContextService;
    private final ChatSkillContextService chatSkillContextService;
    private final ChatKnowledgeContextService chatKnowledgeContextService;
    private final FunctionCallingService functionCallingService;
    private final RolePolicyService rolePolicyService;
    private final TraceService traceService;
    private final CreditsPolicyEvaluator creditsPolicyEvaluator;
    private final CreditsRuntimeService creditsRuntimeService;
    @jakarta.annotation.PreDestroy
    void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
    private final String defaultModel;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatSseController(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            AuditService auditService,
            AiUsageService usageService,
            UserRepository userRepo,
            PlatformTransactionManager transactionManager,
            RolePolicyService rolePolicyService,
            ChatSseRequestSupport chatSseRequestSupport,
            ChatAttachmentPreprocessor attachmentPreprocessor,
            ChatMcpToolContextService chatMcpToolContextService,
            ChatSkillContextService chatSkillContextService,
            ChatKnowledgeContextService chatKnowledgeContextService,
            FunctionCallingService functionCallingService,
            @Nullable TraceService traceService,
            @Nullable CreditsPolicyEvaluator creditsPolicyEvaluator,
            @Nullable CreditsRuntimeService creditsRuntimeService,
            @Value("${ai.default-model:}") String defaultModel
    ) {
        this(objectMapper, adapterFactory, channelRouter, cryptoService, ssrfGuard, channelMonitor, auditService,
                usageService, userRepo, transactionManager, rolePolicyService, chatSseRequestSupport, attachmentPreprocessor, chatMcpToolContextService, chatSkillContextService, chatKnowledgeContextService, functionCallingService, traceService,
                creditsPolicyEvaluator, creditsRuntimeService, defaultModel, createExecutor());
    }

    ChatSseController(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            AuditService auditService,
            AiUsageService usageService,
            UserRepository userRepo,
            PlatformTransactionManager transactionManager,
            RolePolicyService rolePolicyService,
            ChatSseRequestSupport chatSseRequestSupport,
            ChatAttachmentPreprocessor attachmentPreprocessor,
            ChatMcpToolContextService chatMcpToolContextService,
            ChatSkillContextService chatSkillContextService,
            ChatKnowledgeContextService chatKnowledgeContextService,
            FunctionCallingService functionCallingService,
            @Nullable TraceService traceService,
            @Nullable CreditsPolicyEvaluator creditsPolicyEvaluator,
            @Nullable CreditsRuntimeService creditsRuntimeService,
            String defaultModel,
            ExecutorService executor
    ) {
        this.objectMapper = objectMapper;
        this.adapterFactory = adapterFactory;
        this.channelRouter = channelRouter;
        this.cryptoService = cryptoService;
        this.ssrfGuard = ssrfGuard;
        this.channelMonitor = channelMonitor;
        this.auditService = auditService;
        this.usageService = usageService;
        this.userRepo = userRepo;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.defaultModel = defaultModel == null ? "" : defaultModel.trim();
        this.chatSseEventService = new ChatSseEventService(channelMonitor, auditService);
        this.chatSseRequestSupport = chatSseRequestSupport;
        this.attachmentPreprocessor = attachmentPreprocessor;
        this.chatMcpToolContextService = chatMcpToolContextService;
        this.chatSkillContextService = chatSkillContextService;
        this.chatKnowledgeContextService = chatKnowledgeContextService;
        this.functionCallingService = functionCallingService;
        this.rolePolicyService = rolePolicyService;
        this.traceService = traceService == null ? TraceService.noop() : traceService;
        this.creditsPolicyEvaluator = creditsPolicyEvaluator;
        this.creditsRuntimeService = creditsRuntimeService;
    }

    @PostMapping(value = "/completions/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestAttribute(name = RequestIdFilter.TRACE_ATTR_KEY, required = false) String traceId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return errorEmitter("unauthorized");

        String requestId = resolveRequestId(request);
        String normalizedTraceId = normalizeTraceId(traceId);
        SseEmitter emitter = SseEmitterFactory.create(Duration.ofMinutes(10).toMillis());

        Map<String, Object> preparedReqBody;
        try {
            preparedReqBody = chatSseRequestSupport.prepareStreamRequestBody(body, defaultModel);
        } catch (IllegalStateException e) {
            sendAndComplete(emitter, "chat.error",
                    ChatMessageBlockEnvelopeFactory.errorPayload(requestId, normalizedTraceId, body, e.getMessage()));
            return emitter;
        }
        var attachmentResult = attachmentPreprocessor.process(userId, preparedReqBody);
        if (!attachmentResult.success()) {
            sendAndComplete(emitter, "chat.error",
                    ChatMessageBlockEnvelopeFactory.errorPayload(requestId, normalizedTraceId, preparedReqBody, attachmentResult.error()));
            return emitter;
        }
        Map<String, Object> enrichedReqBody = attachmentResult.processedRequest();
        chatMcpToolContextService.applySavedMcpToolNames(userId, enrichedReqBody);
        chatSkillContextService.applySavedSkillContracts(userId, enrichedReqBody);
        chatKnowledgeContextService.applyKnowledgeContext(userId, enrichedReqBody);
        final Map<String, Object> finalReqBody = enrichedReqBody;
        String directKnowledgeAnswer = ChatSystemPromptSupport.normalizePrompt(
                finalReqBody.remove(ChatKnowledgeContextService.DIRECT_ANSWER_KEY)
        );
        if (directKnowledgeAnswer != null) {
            safeSend(emitter, "chat.delta",
                    ChatMessageBlockEnvelopeFactory.deltaPayload(requestId, normalizedTraceId, finalReqBody, directKnowledgeAnswer));
            sendAndComplete(emitter, "chat.done",
                    ChatMessageBlockEnvelopeFactory.donePayload(requestId, normalizedTraceId, finalReqBody));
            return emitter;
        }

        // Credits policy evaluation
        String effectiveRole = role != null ? role : "user";
        String model = chatSseRequestSupport.resolveModel(finalReqBody);
        CreditsPolicyEvaluator.PolicyDecision creditsDecision = null;
        Long creditsSnapshotId = null;
        if (creditsPolicyEvaluator != null) {
            try {
                creditsDecision = creditsPolicyEvaluator.evaluate(userId, null, effectiveRole, model);
            } catch (Exception e) {
                log.warn("[ai.sse] credits policy check failed (fail-closed): userId={}, error={}", userId, e.toString());
                sendAndComplete(emitter, "chat.error",
                        ChatMessageBlockEnvelopeFactory.errorPayload(requestId, normalizedTraceId, finalReqBody,
                                "credits_policy_unavailable"));
                return emitter;
            }
            if (creditsDecision != null && !creditsDecision.allowed()) {
                sendAndComplete(emitter, "chat.error",
                        ChatMessageBlockEnvelopeFactory.errorPayload(requestId, normalizedTraceId, finalReqBody,
                                CreditsErrorSupport.resolve(creditsDecision.denialReason()).reason()));
                return emitter;
            }
            if (creditsDecision != null && creditsDecision.creditsRequired() && creditsRuntimeService != null) {
                try {
                creditsSnapshotId = creditsRuntimeService.reserve(userId, null, effectiveRole, model,
                            creditsDecision.modelMeta(), creditsDecision.account(), requestId);
                } catch (Exception e) {
                    sendAndComplete(emitter, "chat.error",
                            ChatMessageBlockEnvelopeFactory.errorPayload(requestId, normalizedTraceId, finalReqBody,
                                    "credits_reserve_failed"));
                    return emitter;
                }
                if (creditsSnapshotId == null) {
                    sendAndComplete(emitter, "chat.error",
                            ChatMessageBlockEnvelopeFactory.errorPayload(requestId, normalizedTraceId, finalReqBody,
                                    "credits_reserve_failed"));
                    return emitter;
                }
            }
        }

        String ip = RequestUtils.clientIp(request);
        String ua = RequestUtils.userAgent(request);
        final Long finalCreditsSnapshotId = creditsSnapshotId;
        if (!submitStreamTask(() -> stream(userId, finalReqBody, requestId, normalizedTraceId, emitter, ip, ua, finalCreditsSnapshotId))) {
            if (finalCreditsSnapshotId != null && creditsRuntimeService != null) {
                try { creditsRuntimeService.refund(finalCreditsSnapshotId); } catch (Exception ignored) {}
            }
            sendAndComplete(emitter, "chat.error",
                    ChatMessageBlockEnvelopeFactory.errorPayload(requestId, normalizedTraceId, finalReqBody, "service busy"));
        }
        return emitter;
    }

    private void stream(UUID userId, Map<String, Object> reqBody, String requestId, String traceId, SseEmitter emitter,
                        String ip, String userAgent, Long creditsSnapshotId) {
        Long reservedTokens;
        try {
            reservedTokens = reserveStreamingQuota(userId, reqBody);
        } catch (IllegalStateException e) {
            if (creditsSnapshotId != null && creditsRuntimeService != null) {
                try { creditsRuntimeService.refund(creditsSnapshotId); } catch (Exception ignored) {}
            }
            sendAndComplete(emitter, "chat.error",
                    ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, reqBody, e.getMessage()));
            return;
        }
        streamViaRouter(userId, reqBody, requestId, traceId, emitter, ip, userAgent, reservedTokens, creditsSnapshotId);
    }

    private void streamViaRouter(UUID userId, Map<String, Object> requestBody, String requestId, String traceId, SseEmitter emitter,
                                 String ip, String userAgent, long reservedTokens, Long creditsSnapshotId) {
        Instant startedAt = Instant.now();

        String model = chatSseRequestSupport.resolveModel(requestBody);
        traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse",
                startedAt, null, "started", Map.of(
                        "model", model,
                        "messageCount", messageCount(requestBody)
                ));

        boolean sentAnyDelta = false;
        boolean reservationFinalized = false;
        String endStatus = "";
        String endError = "";
        Long channelId = null;
        Long keyId = null;
        String channelType = null;
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        Set<Long> excludedChannels = new HashSet<>();
        Set<Long> excludedKeys = new HashSet<>();

        try {
            for (int attempt = 1; attempt <= MAX_SELECTION_ATTEMPTS; attempt++) {
                endStatus = "";
                endError = "";

                ChannelSelection selection;
                try {
                    selection = channelRouter.select(model == null ? "" : model, excludedChannels, excludedKeys);
                } catch (NoChannelException e) {
                    endStatus = "no_channel";
                    endError = e.getMessage() == null ? "no_channel" : e.getMessage();
                    sendAndComplete(emitter, "chat.error",
                            ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody, "No available channel"));
                    traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.end",
                            startedAt, Instant.now(), "no_channel", Map.of("error", endError));
                    audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                            0, 0, 0);
                    return;
                }

                channelId = selection.channel() == null ? null : selection.channel().getId();
                channelType = selection.channel() == null ? null : selection.channel().getType();
                keyId = selection.key() == null ? null : selection.key().getId();

                ProviderAdapter adapter;
                try {
                    adapter = adapterFactory.get(channelType);
                } catch (Exception e) {
                    endStatus = "unsupported_adapter";
                    endError = e.getMessage();
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    monitorFailure(channelId, keyId, endStatus, null, endError);
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
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    monitorFailure(channelId, keyId, endStatus, null, endError);
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
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    monitorFailure(channelId, keyId, endStatus, null, endError);
                    if (channelId != null) {
                        excludedChannels.add(channelId);
                    }
                    continue;
                }

                Map<String, Object> req = chatSseRequestSupport.buildUpstreamRequestBody(requestBody, selection);

                if (functionCallingService != null && functionCallingService.supports(req, channelType)) {
                    try {
                        FunctionCallingService.FunctionCallingOutcome outcome =
                                functionCallingService.execute(selection.channel(), apiKey, req);
                        int finalTotal = outcome.totalTokens() > 0
                                ? outcome.totalTokens()
                                : (outcome.promptTokens() + outcome.completionTokens());
                        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
                        String finalContent = outcome.content() == null ? "" : outcome.content();
                        if (!finalContent.isEmpty()) {
                            if (!safeSend(emitter, "chat.delta",
                                    ChatMessageBlockEnvelopeFactory.deltaPayload(requestId, traceId, requestBody, finalContent))) {
                                endStatus = "client_closed";
                                endError = "emitter_send_failed";
                                if (reservedTokens > 0) {
                                    protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                            outcome.promptTokens(), outcome.completionTokens(), finalTotal, sentAnyDelta);
                                }
                                if (creditsSnapshotId != null && creditsRuntimeService != null) {
                                    try {
                                        creditsRuntimeService.partialSettle(creditsSnapshotId,
                                                outcome.promptTokens(), outcome.completionTokens());
                                    } catch (Exception ignored) {
                                    }
                                }
                                traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.client_closed",
                                        startedAt, Instant.now(), "client_closed", Map.of("error", endError));
                                audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                        outcome.promptTokens(), outcome.completionTokens(), finalTotal);
                                return;
                            }
                            sentAnyDelta = true;
                        }
                        if (reservedTokens > 0) {
                            finalizeReservedQuotaAfterStream(userId, reservedTokens, channelId, channelType, model,
                                    outcome.promptTokens(), outcome.completionTokens(), finalTotal, latencyMs, true, requestId,
                                    finalTotal > 0, sentAnyDelta);
                        } else {
                            logUsageAndDeductQuota(userId, channelId, channelType, model,
                                    outcome.promptTokens(), outcome.completionTokens(), finalTotal, latencyMs, true, requestId);
                        }
                        reservationFinalized = true;
                        if (creditsSnapshotId != null && creditsRuntimeService != null) {
                            try {
                                creditsRuntimeService.settle(creditsSnapshotId, 1, 1,
                                        outcome.promptTokens(), outcome.completionTokens());
                            } catch (Exception e2) {
                                log.warn("[ai.sse] credits settle failed: snapshotId={}", creditsSnapshotId, e2);
                            }
                        }
                        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(
                                ChatMessageBlockEnvelopeFactory.donePayload(requestId, traceId, requestBody));
                        payload.put("toolTrace", outcome.toolTrace());
                        sendAndComplete(emitter, "chat.done", payload);
                        traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.tool",
                                startedAt, Instant.now(), "ok", Map.of(
                                        "channelId", channelId == null ? "" : String.valueOf(channelId),
                                        "keyId", keyId == null ? "" : String.valueOf(keyId),
                                        "promptTokens", outcome.promptTokens(),
                                        "completionTokens", outcome.completionTokens(),
                                        "totalTokens", finalTotal
                                ));
                        monitorSuccess(channelId, keyId);
                        if (rolePolicyService != null) {
                            rolePolicyService.incrementDailyUsage(userId);
                        }
                        audit(userId, requestId, model, channelId, channelType, keyId, "done", "", startedAt, ip, userAgent,
                                outcome.promptTokens(), outcome.completionTokens(), finalTotal);
                        return;
                    } catch (FunctionCallingService.FunctionCallingException e) {
                        endStatus = "function_calling_failed";
                        endError = e.getMessage() == null ? "function_calling_failed" : e.getMessage();
                        logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                        monitorFailure(channelId, keyId, endStatus, null, endError);
                        if (!sentAnyDelta && attempt < MAX_SELECTION_ATTEMPTS && e.isChannelFailure()) {
                            if (channelId != null) {
                                excludedChannels.add(channelId);
                            }
                            if (keyId != null) {
                                excludedKeys.add(keyId);
                            }
                            continue;
                        }
                        if (reservedTokens > 0) {
                            protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                    promptTokens, completionTokens, totalTokens, sentAnyDelta);
                        }
                        if (creditsSnapshotId != null && creditsRuntimeService != null) {
                            try {
                                if (sentAnyDelta) {
                                    creditsRuntimeService.partialSettle(creditsSnapshotId, promptTokens, completionTokens);
                                } else {
                                    creditsRuntimeService.refund(creditsSnapshotId);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        sendAndComplete(emitter, "chat.error",
                                ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                                        "Service temporarily unavailable; please try again later"));
                        traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.exception",
                                startedAt, Instant.now(), endStatus, Map.of("error", endError));
                        audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                promptTokens, completionTokens, totalTokens);
                        return;
                    }
                }

                HttpRequest upstreamReq;
                try {
                    upstreamReq = adapter.buildChatRequest(req, selection.channel(), apiKey, true);
                } catch (Exception e) {
                    endStatus = "build_request_failed";
                    endError = e.getMessage() == null ? "build_request_failed" : e.getMessage();
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    monitorFailure(channelId, keyId, endStatus, null, endError);
                    if (channelId != null) {
                        excludedChannels.add(channelId);
                    }
                    if (keyId != null) {
                        excludedKeys.add(keyId);
                    }
                    continue;
                }

                try {
                    HttpResponse<InputStream> resp = httpClient.send(upstreamReq, HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream body = resp.body()) {
                        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                            endStatus = "http_error";
                            endError = "status=" + resp.statusCode();
                            logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, resp.statusCode());
                            monitorFailure(channelId, keyId, endStatus, resp.statusCode(), endError);
                            if (!sentAnyDelta && attempt < MAX_SELECTION_ATTEMPTS) {
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
                                    // No alternative channel: fail fast instead of spinning through selection attempts.
                                    sendAndComplete(emitter, "chat.error",
                                            ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                                                    ChatStreamProcessor.resolveUserFacingErrorMessage("http_error", status, endError)));
                                    traceService.recordSpan(userId, traceId, requestId, null, "provider.chat.http_error",
                                            startedAt, Instant.now(), "http_error", Map.of(
                                                    "channelId", channelId,
                                                    "keyId", keyId,
                                                    "channelType", channelType,
                                                    "status", status
                                            ));
                                    audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                            promptTokens, completionTokens, totalTokens);
                                    return;
                                }
                                excludeForHttpError(status, channelId, keyId, excludedChannels, excludedKeys);
                                continue;
                            }
                            if (reservedTokens > 0) {
                                protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                        promptTokens, completionTokens, totalTokens, sentAnyDelta);
                            }
                            sendAndComplete(emitter, "chat.error",
                                    ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                                            "Service temporarily unavailable; please try again later"));
                            traceService.recordSpan(userId, traceId, requestId, null, "provider.chat.http_error",
                                    startedAt, Instant.now(), "http_error", Map.of(
                                            "channelId", channelId,
                                            "keyId", keyId,
                                            "channelType", channelType,
                                            "status", resp.statusCode()
                                    ));
                            audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                    promptTokens, completionTokens, totalTokens);
                            return;
                        }

                        try (BufferedReader br = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                            String line;
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
                                    endStatus = "upstream_error";
                                    endError = chunk.errorMessage();
                                    throw new IllegalStateException("upstream_error");
                                }
                                if (chunk.delta() != null && !chunk.delta().isEmpty()) {
                                if (!safeSend(emitter, "chat.delta",
                                        ChatMessageBlockEnvelopeFactory.deltaPayload(requestId, traceId, requestBody, chunk.delta()))) {
                                        endStatus = "client_closed";
                                        endError = "emitter_send_failed";
                                        if (reservedTokens > 0) {
                                            protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                                    promptTokens, completionTokens, totalTokens, sentAnyDelta);
                                        }
                                        if (creditsSnapshotId != null && creditsRuntimeService != null) {
                                            try { creditsRuntimeService.partialSettle(creditsSnapshotId, promptTokens, completionTokens); }
                                            catch (Exception ignored) {}
                                        }
                                        traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.client_closed",
                                                startedAt, Instant.now(), "client_closed", Map.of("error", endError));
                                        audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                                promptTokens, completionTokens, totalTokens);
                                        return;
                                    }
                                    sentAnyDelta = true;
                                }
                                if (chunk.reasoningDelta() != null && !chunk.reasoningDelta().isEmpty()) {
                                    safeSend(emitter, "chat.thinking",
                                            ChatMessageBlockEnvelopeFactory.thinkingPayload(requestId, traceId, requestBody, chunk.reasoningDelta()));
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
                        }
                    }

                    long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
                    int finalTotal = totalTokens > 0 ? totalTokens : (promptTokens + completionTokens);
                    try {
                        if (reservedTokens > 0) {
                            finalizeReservedQuotaAfterStream(userId, reservedTokens, channelId, channelType, model,
                                    promptTokens, completionTokens, finalTotal, latencyMs, true, requestId,
                                    finalTotal > 0, sentAnyDelta);
                        } else {
                            logUsageAndDeductQuota(userId, channelId, channelType, model,
                                    promptTokens, completionTokens, finalTotal, latencyMs, true, requestId);
                        }
                        reservationFinalized = true;
                    } catch (RuntimeException e) {
                        endStatus = "usage_quota_failed";
                        endError = e.getMessage() == null ? "usage_quota_failed" : e.getMessage();
                        if (creditsSnapshotId != null && creditsRuntimeService != null) {
                            try { creditsRuntimeService.refund(creditsSnapshotId); } catch (Exception ignored) {}
                        }
                        sendAndComplete(emitter, "chat.error",
                                ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody, "usage persistence failed"));
                        traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.usage_persist_failed",
                                startedAt, Instant.now(), "error", Map.of("error", endError));
                        audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                                promptTokens, completionTokens, finalTotal);
                        return;
                    }

                    finishSuccessfulStream(userId, requestId, traceId, model, channelId, channelType, keyId,
                            requestBody, emitter, startedAt, ip, userAgent, promptTokens, completionTokens, totalTokens);
                    // Credits settle on success
                    if (creditsSnapshotId != null && creditsRuntimeService != null) {
                        try { creditsRuntimeService.settle(creditsSnapshotId, 1, 1, promptTokens, completionTokens); }
                        catch (Exception e2) { log.warn("[ai.sse] credits settle failed: snapshotId={}", creditsSnapshotId, e2); }
                    }
                    traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.end",
                            startedAt, Instant.now(), "ok", Map.of(
                                    "promptTokens", promptTokens,
                                    "completionTokens", completionTokens,
                                    "totalTokens", totalTokens
                            ));
                    return;
                } catch (Exception e) {
                    if (endStatus == null || endStatus.isBlank()) {
                        endStatus = "exception";
                    }
                    if (endError == null || endError.isBlank()) {
                        endError = e.getMessage() == null ? "stream_failed" : e.getMessage();
                    }
                    logAttemptFailure(requestId, attempt, channelId, keyId, endStatus, endError, null);
                    monitorFailure(channelId, keyId, endStatus, null, endError);
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
                        protectReservedQuotaOnStreamFailure(userId, reservedTokens,
                                promptTokens, completionTokens, totalTokens, sentAnyDelta);
                    }
                    // Credits refund or partial settle on stream failure
                    if (creditsSnapshotId != null && creditsRuntimeService != null) {
                        try {
                            if (sentAnyDelta) {
                                creditsRuntimeService.partialSettle(creditsSnapshotId, promptTokens, completionTokens);
                            } else {
                                creditsRuntimeService.refund(creditsSnapshotId);
                            }
                        } catch (Exception ignored) {}
                    }
                    sendAndComplete(emitter, "chat.error",
                            ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                                    "Service temporarily unavailable; please try again later"));
                    traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.exception",
                            startedAt, Instant.now(), "exception", Map.of("error", endError));
                    audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                            promptTokens, completionTokens, totalTokens);
                    return;
                }
            }
        } finally {
            if (reservedTokens > 0 && !reservationFinalized && !sentAnyDelta) {
                try {
                    releaseReservedQuota(userId, reservedTokens);
                } catch (RuntimeException e) {
                    log.warn("[ai.sse] release reserved quota failed: userId={}, requestId={}, error={}",
                            userId, requestId, e.toString());
                }
            }
            // Credits final safety net: refund if not finalized
            if (creditsSnapshotId != null && !reservationFinalized && !sentAnyDelta && creditsRuntimeService != null) {
                try { creditsRuntimeService.refund(creditsSnapshotId); } catch (Exception ignored) {}
            }
        }

        endStatus = "exhausted";
        if (endError == null || endError.isBlank()) {
            endError = "no_upstream_available";
        }
        sendAndComplete(emitter, "chat.error",
                ChatMessageBlockEnvelopeFactory.errorPayload(requestId, traceId, requestBody,
                        "No upstream channel available; please try again later"));
        traceService.recordSpan(userId, traceId, requestId, null, "chat.completions.sse.end",
                startedAt, Instant.now(), "exhausted", Map.of("error", endError));
        audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                promptTokens, completionTokens, totalTokens);
    }

    private void audit(UUID userId, String requestId, String model, Long channelId, String channelType, Long keyId,
                       String status, String error, Instant startedAt, String ip, String userAgent,
                       int promptTokens, int completionTokens, int totalTokens) {
        chatSseEventService.audit(userId, requestId, model, channelId, channelType, keyId, status, error,
                startedAt, ip, userAgent, promptTokens, completionTokens, totalTokens);
    }

    private void monitorSuccess(Long channelId, Long keyId) {
        chatSseEventService.monitorSuccess(channelId, keyId);
    }

    private void monitorFailure(Long channelId, Long keyId, String code, Integer httpStatus, String error) {
        chatSseEventService.monitorFailure(channelId, keyId, code, httpStatus, error);
    }

    private void logAttemptFailure(String requestId, int attempt, Long channelId, Long keyId,
                                   String status, String error, Integer httpStatus) {
        log.warn("[ai.sse] attempt failed: requestId={}, attempt={}, channelId={}, keyId={}, status={}, httpStatus={}, error={}",
                requestId, attempt, channelId, keyId, status, httpStatus, error);
    }

    private static void excludeForHttpError(int statusCode, Long channelId, Long keyId, Set<Long> excludedChannels, Set<Long> excludedKeys) {
        ChatSseEventService.excludeForHttpError(statusCode, channelId, keyId, excludedChannels, excludedKeys);
    }

    Long reserveStreamingQuota(UUID userId, Map<String, Object> requestBody) {
        try {
            UserEntity user = userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
            if (user == null) {
                throw new IllegalStateException("User not found");
            }
            long quota = user.getTokenQuota();
            if (quota <= 0) {
                return 0L;
            }
            long remaining = quota - user.getTokenUsed();
            if (remaining <= 0) {
                throw new IllegalStateException("Token quota exhausted; contact administrator");
            }
            Integer updated = transactionTemplate.execute(status -> userRepo.incrementTokenUsedIfWithinQuota(userId, remaining));
            if (updated == null || updated <= 0) {
                throw new IllegalStateException("Token quota exhausted; contact administrator");
            }
            chatSseRequestSupport.applyStreamingQuotaLimit(requestBody, remaining);
            return remaining;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ai.sse] quota reserve failed (fail-closed): userId={}, error={}", userId, e.toString());
            throw new IllegalStateException("Quota lookup failed; please try again later", e);
        }
    }

    private void logUsageAndDeductQuota(UUID userId, Long channelId, String channelType, String model,
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
                    log.warn("[ai.sse] protect partial stream reservation failed: userId={}, requestId={}, error={}",
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

    private static String resolveRequestId(HttpServletRequest request) {
        Object reqIdAttr = request == null ? null : request.getAttribute(RequestIdFilter.ATTR_KEY);
        if (reqIdAttr != null) {
            String id = String.valueOf(reqIdAttr).trim();
            if (!id.isEmpty()) {
                return id;
            }
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalizeTraceId(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static int messageCount(Map<String, Object> req) {
        if (req == null) return 0;
        Object messages = req.get("messages");
        if (messages instanceof Collection<?> c) return c.size();
        return 0;
    }

    private static SseEmitter errorEmitter(String message) {
        return SseEmitterFactory.errorEmitter(log, message);
    }

    void finishSuccessfulStream(UUID userId, String requestId, String traceId, String model,
                                Long channelId, String channelType, Long keyId,
                                Map<String, Object> requestBody, SseEmitter emitter, Instant startedAt, String ip, String userAgent,
                                int promptTokens, int completionTokens, int totalTokens) {
        chatSseEventService.finishSuccessfulStream(userId, requestId, traceId, model, channelId, channelType, keyId,
                requestBody, emitter, startedAt, ip, userAgent, promptTokens, completionTokens, totalTokens, ChatSseController::safeSend);
        // Increment daily usage after successful stream
        if (rolePolicyService != null) {
            rolePolicyService.incrementDailyUsage(userId);
        }
    }

    void finishSuccessfulStream(UUID userId, String requestId, String traceId, String model,
                                Long channelId, String channelType, Long keyId,
                                SseEmitter emitter, Instant startedAt, String ip, String userAgent,
                                int promptTokens, int completionTokens, int totalTokens) {
        finishSuccessfulStream(userId, requestId, traceId, model, channelId, channelType, keyId,
                null, emitter, startedAt, ip, userAgent, promptTokens, completionTokens, totalTokens);
    }

    private static boolean safeSend(SseEmitter emitter, String event, Object data) {
        return SseEmitterFactory.safeSend(log, emitter, event, data);
    }

    private static void sendAndComplete(SseEmitter emitter, String event, Object data) {
        SseEmitterFactory.sendAndComplete(log, emitter, event, data);
    }

    boolean submitStreamTask(Runnable task) {
        try {
            executor.submit(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private static ExecutorService createExecutor() {
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 4);
        return new ThreadPoolExecutor(
                poolSize, poolSize, 60L, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(256),
                r -> { Thread t = new Thread(r, "chat-sse-worker"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }

}
