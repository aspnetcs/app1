package com.webchat.platformapi.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.chat.ChatAttachmentPreprocessor;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.credits.CreditsErrorSupport;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import com.webchat.platformapi.credits.CreditsSystemConfig;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * OpenAI-compatible gateway at /v1/*.
 * Third-party clients (OpenAI SDK, NextChat, etc.) can connect directly.
 * Enabled via: platform.openai-gateway=true
 */
@RestController
@RequestMapping("/v1")
@ConditionalOnProperty(name = "platform.openai-gateway", havingValue = "true")
public class OpenAiGatewayController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiGatewayController.class);

    private final UserRepository userRepo;
    private final UserGroupService userGroupService;
    private final RolePolicyService rolePolicyService;
    private final GatewayModelsFacade gatewayModelsFacade;
    private final GatewayQuotaService gatewayQuotaService;
    private final GatewayProxyService gatewayProxyService;
    private final ChatAttachmentPreprocessor attachmentPreprocessor;
    private final CreditsPolicyEvaluator creditsPolicyEvaluator;
    private final CreditsRuntimeService creditsRuntimeService;
    private final boolean userGroupsEnabled;
    private final ExecutorService executor;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.lang.Nullable
    private CreditsSystemConfig creditsSystemConfig;

    @jakarta.annotation.PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public OpenAiGatewayController(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            AiChannelRepository channelRepo,
            UserRepository userRepo,
            AiUsageService usageService,
            UserGroupService userGroupService,
            RolePolicyService rolePolicyService,
            PlatformTransactionManager transactionManager,
            ChatAttachmentPreprocessor attachmentPreprocessor,
            @org.springframework.beans.factory.annotation.Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled,
            @org.springframework.lang.Nullable CreditsPolicyEvaluator creditsPolicyEvaluator,
            @org.springframework.lang.Nullable CreditsRuntimeService creditsRuntimeService
    ) {
        this(
                userRepo,
                userGroupService,
                rolePolicyService,
                GatewaySupportFactory.assemble(
                        objectMapper,
                        adapterFactory,
                        channelRouter,
                        cryptoService,
                        ssrfGuard,
                        channelMonitor,
                        channelRepo,
                        userRepo,
                        usageService,
                        rolePolicyService,
                        transactionManager,
                        GatewaySupportFactory.createHttpClient(),
                        GatewaySupportFactory.createExecutor(),
                        creditsRuntimeService
                ),
                attachmentPreprocessor,
                userGroupsEnabled,
                creditsPolicyEvaluator,
                creditsRuntimeService
        );
    }

    OpenAiGatewayController(
            UserRepository userRepo,
            UserGroupService userGroupService,
            RolePolicyService rolePolicyService,
            GatewaySupportFactory.Assembly assembly,
            boolean userGroupsEnabled,
            CreditsPolicyEvaluator creditsPolicyEvaluator,
            CreditsRuntimeService creditsRuntimeService
    ) {
        this(userRepo, userGroupService, rolePolicyService, assembly, noOpAttachmentPreprocessor(),
                userGroupsEnabled, creditsPolicyEvaluator, creditsRuntimeService);
    }

    OpenAiGatewayController(
            UserRepository userRepo,
            UserGroupService userGroupService,
            RolePolicyService rolePolicyService,
            GatewaySupportFactory.Assembly assembly,
            ChatAttachmentPreprocessor attachmentPreprocessor,
            boolean userGroupsEnabled,
            CreditsPolicyEvaluator creditsPolicyEvaluator,
            CreditsRuntimeService creditsRuntimeService
    ) {
        this.userRepo = userRepo;
        this.userGroupService = userGroupService;
        this.rolePolicyService = rolePolicyService;
        this.userGroupsEnabled = userGroupsEnabled;
        this.creditsPolicyEvaluator = creditsPolicyEvaluator;
        this.creditsRuntimeService = creditsRuntimeService;
        this.executor = assembly.executor();
        this.gatewayModelsFacade = assembly.gatewayModelsFacade();
        this.gatewayQuotaService = assembly.gatewayQuotaService();
        this.gatewayProxyService = assembly.gatewayProxyService();
        this.attachmentPreprocessor = attachmentPreprocessor;
    }

    OpenAiGatewayController(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            AiChannelRepository channelRepo,
            UserRepository userRepo,
            AiUsageService usageService,
            UserGroupService userGroupService,
            RolePolicyService rolePolicyService,
            PlatformTransactionManager transactionManager,
            boolean userGroupsEnabled,
            HttpClient httpClient,
            ExecutorService executor
    ) {
        this(objectMapper, adapterFactory, channelRouter, cryptoService, ssrfGuard, channelMonitor,
                channelRepo, userRepo, usageService, userGroupService, rolePolicyService, transactionManager,
                noOpAttachmentPreprocessor(), userGroupsEnabled, httpClient, executor);
    }

    OpenAiGatewayController(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            AiChannelRepository channelRepo,
            UserRepository userRepo,
            AiUsageService usageService,
            UserGroupService userGroupService,
            RolePolicyService rolePolicyService,
            PlatformTransactionManager transactionManager,
            ChatAttachmentPreprocessor attachmentPreprocessor,
            boolean userGroupsEnabled,
            HttpClient httpClient,
            ExecutorService executor
    ) {
        this(
                userRepo,
                userGroupService,
                rolePolicyService,
                GatewaySupportFactory.assemble(
                        objectMapper,
                        adapterFactory,
                        channelRouter,
                        cryptoService,
                        ssrfGuard,
                        channelMonitor,
                        channelRepo,
                        userRepo,
                        usageService,
                        rolePolicyService,
                        transactionManager,
                        httpClient,
                        executor,
                        null
                ),
                attachmentPreprocessor,
                userGroupsEnabled,
                null,
                null
        );
    }

    OpenAiGatewayController(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            AiChannelRepository channelRepo,
            UserRepository userRepo,
            AiUsageService usageService,
            UserGroupService userGroupService,
            RolePolicyService rolePolicyService,
            PlatformTransactionManager transactionManager,
            boolean userGroupsEnabled,
            @org.springframework.lang.Nullable CreditsPolicyEvaluator creditsPolicyEvaluator,
            @org.springframework.lang.Nullable CreditsRuntimeService creditsRuntimeService
    ) {
        this(objectMapper, adapterFactory, channelRouter, cryptoService, ssrfGuard, channelMonitor,
                channelRepo, userRepo, usageService, userGroupService, rolePolicyService, transactionManager,
                noOpAttachmentPreprocessor(), userGroupsEnabled, creditsPolicyEvaluator, creditsRuntimeService);
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        return gatewayModelsFacade.models(userId, role);
    }

    @PostMapping(value = "/chat/completions")
    public Object chatCompletions(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", Map.of("message", "unauthorized")));
        }

        try {
            UserEntity user = userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", Map.of("message", "unauthorized")));
            }
            if (user.getTokenQuota() > 0 && user.getTokenUsed() >= user.getTokenQuota()) {
                return ResponseEntity.status(429).body(Map.of("error", Map.of("message", "Token quota exceeded")));
            }
        } catch (Exception e) {
            log.warn("[v1] quota check failed (fail-closed): userId={}", userId);
            return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "Quota service unavailable")));
        }

        // Credits policy evaluation
        Long creditsSnapshotId = null;
        String effectiveRole = normalizeRole(role);
        if (creditsPolicyEvaluator != null) {
            String modelForCredits = body == null ? "" : String.valueOf(body.getOrDefault("model", "")).trim();
            CreditsPolicyEvaluator.PolicyDecision decision = null;
            try {
                decision = creditsPolicyEvaluator.evaluate(userId, null, effectiveRole, modelForCredits);
            } catch (Exception e) {
                log.warn("[v1] credits policy check failed (fail-closed): userId={}, error={}", userId, e.toString());
                var error = CreditsErrorSupport.resolve("credits_policy_unavailable");
                return ResponseEntity.status(error.httpStatus())
                        .body(CreditsErrorSupport.gatewayErrorBody(error.reason()));
            }
            if (decision != null) {
                if (!decision.allowed()) {
                    var error = CreditsErrorSupport.resolve(decision.denialReason());
                    return ResponseEntity.status(error.httpStatus())
                            .body(CreditsErrorSupport.gatewayErrorBody(error.reason()));
                }
                if (decision.creditsRequired() && creditsRuntimeService != null) {
                    String creditsRequestId = "gw_" + UUID.randomUUID().toString().replace("-", "");
                    try {
                        creditsSnapshotId = creditsRuntimeService.reserve(
                                userId,
                                null,
                                effectiveRole,
                                modelForCredits,
                                decision.modelMeta(),
                                decision.account(),
                                creditsRequestId
                        );
                    } catch (Exception e) {
                        log.warn("[v1] credits reserve failed: userId={}, error={}", userId, e.toString());
                        var error = CreditsErrorSupport.resolve("credits_reserve_failed");
                        return ResponseEntity.status(error.httpStatus())
                                .body(CreditsErrorSupport.gatewayErrorBody(error.reason()));
                    }
                    if (creditsSnapshotId == null) {
                        var error = CreditsErrorSupport.resolve("credits_reserve_failed");
                        return ResponseEntity.status(error.httpStatus())
                                .body(CreditsErrorSupport.gatewayErrorBody(error.reason()));
                    }
                }
            }
        }

        Map<String, Object> requestBody = body == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(body);
        var attachmentResult = attachmentPreprocessor.process(userId, requestBody);
        if (!attachmentResult.success()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of(
                            "message", attachmentResult.error(),
                            "code", "attachment_processing_failed"
                    )));
        }
        requestBody = attachmentResult.processedRequest();
        boolean stream = Boolean.TRUE.equals(requestBody.get("stream"));
        String model = requestBody.get("model") == null ? "" : String.valueOf(requestBody.get("model")).trim();
        if (rolePolicyService != null && shouldEnforceRoleModelPolicy()) {
            try {
                Set<String> allowed = rolePolicyService.resolveAllowedModels(userId, effectiveRole);
                if (!allowed.isEmpty() && !allowed.contains(model)) {
                    var error = CreditsErrorSupport.resolve("model_not_allowed");
                    return ResponseEntity.status(error.httpStatus())
                            .body(CreditsErrorSupport.gatewayErrorBody(error.reason()));
                }
            } catch (Exception e) {
                log.warn("[v1] chat role policy failed (fail-closed): userId={}", userId);
                return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "policy unavailable")));
            }
        }
        if (userGroupsEnabled) {
            try {
                if (hasFunctionCallingPayload(requestBody) && !userGroupService.isFeatureAllowed(userId, "function_calling")) {
                    return ResponseEntity.status(403).body(Map.of("error", Map.of("message", "function calling not allowed by group policy")));
                }
            } catch (Exception e) {
                log.warn("[v1] chat group policy failed (fail-closed): userId={}", userId);
                return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "group policy unavailable")));
            }
        }

        return stream
                ? streamChat(requestBody, model, userId, creditsSnapshotId)
                : nonStreamChat(requestBody, model, userId, creditsSnapshotId);
    }

    private SseEmitter streamChat(Map<String, Object> body, String model, UUID userId, Long creditsSnapshotId) {
        return gatewayProxyService.streamChat(
                createEmitter(Duration.ofMinutes(10).toMillis()),
                body,
                model,
                userId,
                this::submitStreamTask,
                creditsSnapshotId
        );
    }

    private ResponseEntity<?> nonStreamChat(Map<String, Object> body, String model, UUID userId, Long creditsSnapshotId) {
        ResponseEntity<?> result = gatewayProxyService.nonStreamChat(body, model, userId);
        // Credits settle/refund for non-streaming
        if (creditsSnapshotId != null && creditsRuntimeService != null) {
            try {
                if (result.getStatusCode().is2xxSuccessful()) {
                    creditsRuntimeService.settle(creditsSnapshotId, 1, 0, 0, 0);
                } else {
                    creditsRuntimeService.refund(creditsSnapshotId);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    void persistUsageAndDeductQuota(UUID userId, Long channelId, String model,
                                    int promptTokens, int completionTokens, int totalTokens,
                                    String requestId) {
        gatewayQuotaService.persistUsageAndDeductQuota(
                userId,
                channelId,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                requestId
        );
    }

    long reserveStreamingQuota(UUID userId, Map<String, Object> requestBody) {
        return gatewayQuotaService.reserveStreamingQuota(userId, requestBody);
    }

    void releaseReservedQuota(UUID userId, long reservedTokens) {
        gatewayQuotaService.releaseReservedQuota(userId, reservedTokens);
    }

    void logUsageAndFinalizeReservedQuota(UUID userId, long reservedTokens, Long channelId, String model,
                                          int promptTokens, int completionTokens, int totalTokens,
                                          String requestId, boolean usageKnown) {
        gatewayQuotaService.logUsageAndFinalizeReservedQuota(
                userId,
                reservedTokens,
                channelId,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                requestId,
                usageKnown
        );
    }

    void finalizeReservedQuotaAfterStream(UUID userId, long reservedTokens, Long channelId, String model,
                                          int promptTokens, int completionTokens, int totalTokens,
                                          String requestId, boolean usageKnown, boolean sentAnyDelta) {
        gatewayQuotaService.finalizeReservedQuotaAfterStream(
                userId,
                reservedTokens,
                channelId,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                requestId,
                usageKnown,
                sentAnyDelta
        );
    }

    void protectReservedQuotaOnStreamFailure(UUID userId, long reservedTokens,
                                             int promptTokens, int completionTokens, int totalTokens,
                                             boolean sentAnyDelta) {
        gatewayQuotaService.protectReservedQuotaOnStreamFailure(
                userId,
                reservedTokens,
                promptTokens,
                completionTokens,
                totalTokens,
                sentAnyDelta
        );
    }

    void protectPartialStreamReservation(UUID userId, long reservedTokens, int totalTokens) {
        gatewayQuotaService.protectPartialStreamReservation(userId, reservedTokens, totalTokens);
    }

    boolean submitStreamTask(Runnable task) {
        try {
            executor.submit(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    SseEmitter createEmitter(long timeoutMs) {
        return new SseEmitter(timeoutMs);
    }

    private static boolean hasFunctionCallingPayload(Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        Object tools = body.get("tools");
        if (tools instanceof Collection<?> collection && !collection.isEmpty()) {
            return true;
        }
        Object functions = body.get("functions");
        return functions instanceof Collection<?> collection && !collection.isEmpty();
    }

    private boolean shouldEnforceRoleModelPolicy() {
        return creditsSystemConfig == null || !creditsSystemConfig.isFreeModeEnabled();
    }

    private static ChatAttachmentPreprocessor noOpAttachmentPreprocessor() {
        var properties = new com.webchat.platformapi.ai.multimodal.MultimodalUploadProperties();
        properties.setEnabled(false);
        return new ChatAttachmentPreprocessor(properties, null, null, null, null);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        return "pending".equals(normalized) ? "user" : normalized;
    }
}
