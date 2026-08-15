package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyEntity;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.extension.FunctionCallingService;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.ws.WsSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaTransactionBoundaryTest {

    @Test
    void userRepositoryIncrementRequiresActiveTransaction() throws Exception {
        Method method = UserRepository.class.getMethod("incrementTokenUsedIfWithinQuota", UUID.class, long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    @Test
    void userRepositoryDecrementRequiresActiveTransaction() throws Exception {
        Method method = UserRepository.class.getMethod("decrementTokenUsed", UUID.class, long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    @Test
    void aiUsageServiceLogUsageStrictRequiresActiveTransaction() throws Exception {
        Method method = AiUsageService.class.getMethod("logUsageStrict", UUID.class, Long.class, String.class, String.class,
                int.class, int.class, long.class, boolean.class, String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    @Test
    void aiUsageServiceLogUsageSupportsNonTransactionalGatewayCalls() {
        var repository = mock(com.webchat.platformapi.ai.usage.AiUsageLogRepository.class);
        AiUsageService service = new AiUsageService(repository);
        UUID userId = UUID.randomUUID();

        assertDoesNotThrow(() -> service.logUsage(userId, 1L, "gateway", "model",
                10, 20, 40L, true, "req-gateway"));

        verify(repository).save(any());
    }

    @Test
    void aiChatServiceWrapsUsageAndQuotaInOneTransaction() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 30)).thenReturn(1);

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        service.persistUsageAndDeductQuota(userId, 1L, "test", "model",
                10, 20, 30, 40L, true, "req-1");

        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-1");
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 30);
    }

    @Test
    void aiChatServiceRollsBackTransactionWhenUsageLoggingFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        doThrow(new IllegalStateException("log fail"))
                .when(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-log");

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        assertThrows(IllegalStateException.class, () ->
                service.persistUsageAndDeductQuota(userId, 1L, "test", "model",
                        10, 20, 30, 40L, true, "req-log"));

        assertEquals(1, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(1, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-log");
        verify(userRepository, never()).incrementTokenUsedIfWithinQuota(userId, 30);
    }

    @Test
    void aiChatServiceReservesRemainingQuotaBeforeStreamingAndClampsExistingLimit() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WsSessionRegistry wsSessionRegistry = mock(WsSessionRegistry.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(100);
        user.setTokenUsed(60);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("max_tokens", 200);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 40)).thenReturn(1);

        AiChatService service = createService(transactionManager, usageService, userRepository, wsSessionRegistry);

        Long reserved = service.reserveStreamingQuota(userId, "req-stream", "trace_quota_1", requestBody);

        assertEquals(40L, reserved);
        assertEquals(40, requestBody.get("max_tokens"));
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 40);
        verify(wsSessionRegistry, never()).sendToUser(eq(userId), eq("chat.error"), any());
    }

    @Test
    void aiChatServiceReservesRemainingQuotaAndSetsDefaultLimitWhenMissing() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WsSessionRegistry wsSessionRegistry = mock(WsSessionRegistry.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(120);
        user.setTokenUsed(75);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 45)).thenReturn(1);

        AiChatService service = createService(transactionManager, usageService, userRepository, wsSessionRegistry);

        Long reserved = service.reserveStreamingQuota(userId, "req-default-limit", "trace_quota_2", requestBody);

        assertEquals(45L, reserved);
        assertEquals(45, requestBody.get("max_tokens"));
        assertNull(requestBody.get("max_completion_tokens"));
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 45);
        verify(wsSessionRegistry, never()).sendToUser(eq(userId), eq("chat.error"), any());
    }

    @Test
    void aiChatServiceRejectsStreamingBeforeOutputWhenQuotaReserveFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WsSessionRegistry wsSessionRegistry = mock(WsSessionRegistry.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(100);
        user.setTokenUsed(90);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 10)).thenReturn(0);

        AiChatService service = createService(transactionManager, usageService, userRepository, wsSessionRegistry);

        Long reserved = service.reserveStreamingQuota(userId, "req-block", "trace_quota_3", new HashMap<>());

        assertNull(reserved);
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 10);
        verify(wsSessionRegistry).sendToUser(eq(userId), eq("chat.error"), any());
    }

    @Test
    void aiChatServiceFinalizesReservedQuotaAndRefundsUnusedAmount() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        when(userRepository.decrementTokenUsed(userId, 70)).thenReturn(1);

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        service.logUsageAndFinalizeReservedQuota(userId, 100, 1L, "test", "model",
                10, 20, 30, 40L, true, "req-finalize", true);

        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-finalize");
        verify(userRepository).decrementTokenUsed(userId, 70);
    }

    @Test
    void aiChatServiceProtectsAgainstFullReservationWhenFinalizeFailsAfterPartialOutput() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        doThrow(new IllegalStateException("log fail"))
                .when(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-partial");
        when(userRepository.decrementTokenUsed(userId, 70)).thenReturn(1);

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.finalizeReservedQuotaAfterStream(userId, 100, 1L, "test", "model",
                        10, 20, 30, 40L, true, "req-partial", true, true));

        assertEquals("log fail", error.getMessage());
        assertEquals(2, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(1, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-partial");
        verify(userRepository).decrementTokenUsed(userId, 70);
    }

    @Test
    void aiChatServiceRethrowsWhenPartialRefundFailsWithoutFullReservationFallback() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        when(userRepository.decrementTokenUsed(userId, 70)).thenReturn(0);

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.protectPartialStreamReservation(userId, 100, 30));

        assertEquals("quota refund skipped", error.getMessage());
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).decrementTokenUsed(userId, 70);
        verify(userRepository, never()).decrementTokenUsed(userId, 100);
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
    }

    @Test
    void aiChatServiceRethrowsWhenFullReservationReleaseFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        when(userRepository.decrementTokenUsed(userId, 100)).thenReturn(0);

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.protectPartialStreamReservation(userId, 100, 0));

        assertEquals("quota refund skipped", error.getMessage());
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).decrementTokenUsed(userId, 100);
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
    }

    @Test
    void aiChatServiceDoesNotRefundWhenStreamFailsAfterPartialOutputEvenWithUsageSnapshot() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        service.protectReservedQuotaOnStreamFailure(userId, 100, 10, 20, 30, true);

        assertEquals(0, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository, never()).decrementTokenUsed(any(), anyLong());
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
    }

    @Test
    void aiChatServiceDoesNotRefundWhenStreamFailsAfterPartialOutputAndUsageIsUnknown() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        service.protectReservedQuotaOnStreamFailure(userId, 100, 0, 0, 0, true);

        assertEquals(0, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository, never()).decrementTokenUsed(any(), anyLong());
    }

    @Test
    void aiChatServiceTreatsUndeliveredDeltaAsSendFailure() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        WsSessionRegistry wsSessionRegistry = mock(WsSessionRegistry.class);
        UUID userId = UUID.randomUUID();

        Map<String, Object> deltaPayload = ChatMessageBlockEnvelopeFactory.deltaPayload(
                "req-send-fail",
                "trace-send-fail",
                null,
                "hello"
        );
        when(wsSessionRegistry.sendToUser(userId, "chat.delta", deltaPayload)).thenReturn(0);

        AiChatService service = createService(transactionManager, usageService, userRepository, wsSessionRegistry);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.sendDeltaOrThrow(userId, "req-send-fail", "trace-send-fail", "hello"));

        assertEquals("chat delta delivery failed", error.getMessage());
        verify(wsSessionRegistry).sendToUser(userId, "chat.delta", deltaPayload);
    }

    @Test
    void aiChatServiceFunctionCallingReleasesReservationWhenFirstDeltaSendFails() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AdapterFactory adapterFactory = mock(AdapterFactory.class);
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        SsrfGuard ssrfGuard = mock(SsrfGuard.class);
        ChannelMonitor channelMonitor = mock(ChannelMonitor.class);
        WsSessionRegistry wsSessionRegistry = mock(WsSessionRegistry.class);
        AuditService auditService = mock(AuditService.class);
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        FunctionCallingService functionCallingService = mock(FunctionCallingService.class);
        UUID userId = UUID.randomUUID();
        String requestId = "req-fc-send-fail";

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(20);
        user.setTokenUsed(0);

        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(11L);
        channel.setType("openai");
        channel.setBaseUrl("https://upstream.example");

        AiChannelKeyEntity key = new AiChannelKeyEntity();
        key.setId(22L);
        key.setApiKeyEncrypted("encrypted-key");

        ChannelSelection selection = new ChannelSelection(channel, key, "gpt-4o");
        FunctionCallingService.FunctionCallingOutcome outcome =
                new FunctionCallingService.FunctionCallingOutcome("hello", java.util.List.of(), 5, 7, 12);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 20)).thenReturn(1);
        when(userRepository.decrementTokenUsed(userId, 20)).thenReturn(1);
        when(channelRouter.select(eq("gpt-4o"), any(), any()))
                .thenReturn(selection)
                .thenThrow(new NoChannelException("no channel"));
        when(adapterFactory.get("openai")).thenReturn(adapter);
        when(cryptoService.decrypt("encrypted-key")).thenReturn("plain-key");
        when(functionCallingService.supports(any(), eq("openai"))).thenReturn(true);
        when(functionCallingService.execute(eq(channel), eq("plain-key"), any())).thenReturn(outcome);
        when(wsSessionRegistry.sendToUser(userId, "chat.delta",
                Map.of("requestId", requestId, "traceId", "trace_test_1", "delta", "hello"))).thenReturn(0);

        AiChatService service = new AiChatService(
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                wsSessionRegistry,
                auditService,
                usageService,
                userRepository,
                functionCallingService,
                null,
                transactionManager,
                mock(com.webchat.platformapi.trace.TraceService.class),
                new ChatStreamContextRegistry(),
                null,
                null,
                1,
                true
        );

        Method streamMethod = AiChatService.class.getDeclaredMethod(
                "stream", UUID.class, String.class, String.class, Map.class, String.class, String.class);
        streamMethod.setAccessible(true);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("toolNames", java.util.List.of("tool-a"));

        streamMethod.invoke(service, userId, requestId, "trace_test_1", requestBody, "127.0.0.1", "JUnit");

        assertEquals(2, transactionManager.beginCount);
        assertEquals(2, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
        verify(userRepository).decrementTokenUsed(userId, 20);
    }

    @Test
    void aiChatServiceFunctionCallingProtectsPartialReservationWhenFinalizeFailsAfterFirstDelta() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AdapterFactory adapterFactory = mock(AdapterFactory.class);
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        SsrfGuard ssrfGuard = mock(SsrfGuard.class);
        ChannelMonitor channelMonitor = mock(ChannelMonitor.class);
        WsSessionRegistry wsSessionRegistry = mock(WsSessionRegistry.class);
        AuditService auditService = mock(AuditService.class);
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        FunctionCallingService functionCallingService = mock(FunctionCallingService.class);
        UUID userId = UUID.randomUUID();
        String requestId = "req-fc-finalize-fail";

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(20);
        user.setTokenUsed(0);

        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(11L);
        channel.setType("openai");
        channel.setBaseUrl("https://upstream.example");

        AiChannelKeyEntity key = new AiChannelKeyEntity();
        key.setId(22L);
        key.setApiKeyEncrypted("encrypted-key");

        ChannelSelection selection = new ChannelSelection(channel, key, "gpt-4o");
        FunctionCallingService.FunctionCallingOutcome outcome =
                new FunctionCallingService.FunctionCallingOutcome("hello", java.util.List.of(), 5, 7, 12);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 20)).thenReturn(1);
        when(userRepository.decrementTokenUsed(userId, 8)).thenReturn(1);
        when(channelRouter.select(eq("gpt-4o"), any(), any())).thenReturn(selection);
        when(adapterFactory.get("openai")).thenReturn(adapter);
        when(cryptoService.decrypt("encrypted-key")).thenReturn("plain-key");
        when(functionCallingService.supports(any(), eq("openai"))).thenReturn(true);
        when(functionCallingService.execute(eq(channel), eq("plain-key"), any())).thenReturn(outcome);
        doThrow(new IllegalStateException("log fail"))
                .when(usageService).logUsageStrict(eq(userId), eq(11L), eq("openai"), eq("gpt-4o"),
                        eq(5), eq(7), anyLong(), eq(true), eq(requestId));

        AiChatService service = new AiChatService(
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                wsSessionRegistry,
                auditService,
                usageService,
                userRepository,
                functionCallingService,
                null,
                transactionManager,
                mock(com.webchat.platformapi.trace.TraceService.class),
                new ChatStreamContextRegistry(),
                null,
                null,
                1,
                true
        );

        Method streamMethod = AiChatService.class.getDeclaredMethod(
                "stream", UUID.class, String.class, String.class, Map.class, String.class, String.class);
        streamMethod.setAccessible(true);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("toolNames", java.util.List.of("tool-a"));
        Map<String, Object> functionDeltaPayload = ChatMessageBlockEnvelopeFactory.deltaPayload(
                requestId, "trace_test_2", requestBody, "hello");
        Map<String, Object> functionErrorPayload = ChatMessageBlockEnvelopeFactory.errorPayload(
                requestId, "trace_test_2", requestBody, "usage persistence failed");
        when(wsSessionRegistry.sendToUser(userId, "chat.delta", functionDeltaPayload)).thenReturn(1);
        when(wsSessionRegistry.sendToUser(userId, "chat.error", functionErrorPayload)).thenReturn(1);

        streamMethod.invoke(service, userId, requestId, "trace_test_2", requestBody, "127.0.0.1", "JUnit");

        assertEquals(3, transactionManager.beginCount);
        assertEquals(2, transactionManager.commitCount);
        assertEquals(1, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(eq(userId), eq(11L), eq("openai"), eq("gpt-4o"),
                eq(5), eq(7), anyLong(), eq(true), eq(requestId));
        verify(userRepository).decrementTokenUsed(userId, 8);
        verify(userRepository, never()).decrementTokenUsed(userId, 20);
        verify(wsSessionRegistry).sendToUser(userId, "chat.error", functionErrorPayload);
    }

    @Test
    void aiChatServiceKeepsReservationWhenUsageIsUnknown() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        AiChatService service = createService(transactionManager, usageService, userRepository, mock(WsSessionRegistry.class));

        service.logUsageAndFinalizeReservedQuota(userId, 100, 1L, "test", "model",
                0, 0, 0, 40L, true, "req-unknown", false);

        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(userId, 1L, "test", "model", 0, 0, 40L, true, "req-unknown");
        verify(userRepository, never()).decrementTokenUsed(any(), anyLong());
    }

    @Test
    void chatSseControllerReservesRemainingQuotaAndSetsDefaultLimitWhenMissing() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(120);
        user.setTokenUsed(75);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 45)).thenReturn(1);

        ChatSseController controller = createChatSseController(transactionManager, usageService, userRepository);

        long reserved = controller.reserveStreamingQuota(userId, requestBody);

        assertEquals(45L, reserved);
        assertEquals(45, requestBody.get("max_tokens"));
        assertNull(requestBody.get("max_completion_tokens"));
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 45);
    }

    @Test
    void chatSseRequestSupportAddsDefaultModelAndStreamFlag() {
        ChatSseRequestSupport support = new ChatSseRequestSupport();

        Map<String, Object> requestBody = support.prepareStreamRequestBody(Map.of("messages", List.of()), "gpt-4o");

        assertEquals("gpt-4o", requestBody.get("model"));
        assertEquals(Boolean.TRUE, requestBody.get("stream"));
    }

    @Test
    void chatSseRequestSupportBuildsUpstreamBodyWithChannelModelAndSystemPrompt() {
        ChatSseRequestSupport support = new ChatSseRequestSupport();
        AiChannelEntity channel = new AiChannelEntity();
        channel.setExtraConfig(Map.of("system_prompt", "Follow the policy"));
        AiChannelKeyEntity key = new AiChannelKeyEntity();
        ChannelSelection selection = new ChannelSelection(channel, key, "gpt-4o-mini");
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("messages", new ArrayList<>(List.of(Map.of("role", "user", "content", "Hello"))));

        Map<String, Object> upstream = support.buildUpstreamRequestBody(requestBody, selection);

        assertEquals(Boolean.TRUE, upstream.get("stream"));
        assertEquals("gpt-4o-mini", upstream.get("model"));
        Object messages = upstream.get("messages");
        assertTrue(messages instanceof List<?>);
        List<?> messageList = (List<?>) messages;
        assertEquals(2, messageList.size());
        assertEquals(Map.of("role", "system", "content", "Follow the policy"), messageList.get(0));
    }

    @Test
    void chatSseControllerRejectsStreamingBeforeOutputWhenQuotaReserveFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(100);
        user.setTokenUsed(90);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 10)).thenReturn(0);

        ChatSseController controller = createChatSseController(transactionManager, usageService, userRepository);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                controller.reserveStreamingQuota(userId, new HashMap<>()));

        assertEquals("Token quota exhausted; contact administrator", error.getMessage());
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 10);
    }

    @Test
    void chatSseControllerProtectsAgainstFullReservationWhenFinalizeFailsAfterPartialOutput() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        doThrow(new IllegalStateException("log fail"))
                .when(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-sse-partial");
        when(userRepository.decrementTokenUsed(userId, 70)).thenReturn(1);

        ChatSseController controller = createChatSseController(transactionManager, usageService, userRepository);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                controller.finalizeReservedQuotaAfterStream(userId, 100, 1L, "test", "model",
                        10, 20, 30, 40L, true, "req-sse-partial", true, true));

        assertEquals("log fail", error.getMessage());
        assertEquals(2, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(1, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(userId, 1L, "test", "model", 10, 20, 40L, true, "req-sse-partial");
        verify(userRepository).decrementTokenUsed(userId, 70);
    }

    @Test
    void chatSseControllerThrowsWhenPartialRefundFails() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        when(userRepository.decrementTokenUsed(userId, 70)).thenReturn(0);

        ChatSseController controller = createChatSseController(transactionManager, usageService, userRepository);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                controller.protectPartialStreamReservation(userId, 100, 30));

        assertEquals("quota refund skipped", error.getMessage());
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).decrementTokenUsed(userId, 70);
        verify(userRepository, never()).decrementTokenUsed(userId, 100);
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
    }

    @Test
    void chatSseControllerDoesNotRefundWhenStreamFailsAfterPartialOutputEvenWithUsageSnapshot() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        ChatSseController controller = createChatSseController(transactionManager, usageService, userRepository);

        controller.protectReservedQuotaOnStreamFailure(userId, 100, 10, 20, 30, true);

        assertEquals(0, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository, never()).decrementTokenUsed(any(), anyLong());
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
    }

    @Test
    void chatSseControllerDoesNotRefundWhenStreamFailsAfterPartialOutputAndUsageIsUnknown() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();

        ChatSseController controller = createChatSseController(transactionManager, usageService, userRepository);

        controller.protectReservedQuotaOnStreamFailure(userId, 100, 0, 0, 0, true);

        assertEquals(0, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository, never()).decrementTokenUsed(any(), anyLong());
    }

    @Test
    void chatSseControllerSafeSendReturnsFalseWhenEmitterThrows() throws Exception {
        Method method = ChatSseController.class.getDeclaredMethod("safeSend", SseEmitter.class, String.class, Object.class);
        method.setAccessible(true);

        boolean sent = (boolean) method.invoke(null,
                new FailingNthEventEmitter(1),
                "chat.done",
                Map.of("requestId", "req-safe-send"));

        assertFalse(sent);
    }

    @Test
    void chatSseControllerMarksDoneSendFailureAsClientClosed() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AiUsageService usageService = mock(AiUsageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChannelMonitor channelMonitor = mock(ChannelMonitor.class);
        AuditService auditService = mock(AuditService.class);
        ChatSseController controller = createChatSseController(
                transactionManager, usageService, userRepository, channelMonitor, auditService);
        UUID userId = UUID.randomUUID();
        FailingNthEventEmitter emitter = new FailingNthEventEmitter(1);

        controller.finishSuccessfulStream(userId, "req-done-fail", "trace-done-fail", "gpt-4o",
                11L, "openai", 22L, emitter, Instant.parse("2026-03-22T00:00:00Z"),
                "127.0.0.1", "JUnit", 5, 7, 12);

        assertTrue(emitter.isCompleted());
        verify(channelMonitor, never()).recordFailure(any(), any(), any(), any(), any());
        verify(channelMonitor, never()).recordSuccess(any(), any());
        verify(auditService).log(eq(userId), eq("ai.chat.sse"),
                argThat(detail -> "req-done-fail".equals(detail.get("requestId"))
                        && "client_closed".equals(detail.get("status"))
                        && "chat_done_delivery_failed".equals(detail.get("error"))),
                eq("127.0.0.1"), eq("JUnit"));
    }

    private static AiChatService createService(RecordingTransactionManager transactionManager,
                                               AiUsageService usageService,
                                               UserRepository userRepository,
                                               WsSessionRegistry wsSessionRegistry) {
        return new AiChatService(
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                wsSessionRegistry,
                mock(AuditService.class),
                usageService,
                userRepository,
                mock(FunctionCallingService.class),
                null,
                transactionManager,
                mock(com.webchat.platformapi.trace.TraceService.class),
                new ChatStreamContextRegistry(),
                null,
                null,
                1,
                true
        );
    }

    private static ChatSseController createChatSseController(RecordingTransactionManager transactionManager,
                                                             AiUsageService usageService,
                                                             UserRepository userRepository) {
        return createChatSseController(transactionManager, usageService, userRepository,
                mock(ChannelMonitor.class), mock(AuditService.class));
    }

    private static ChatSseController createChatSseController(RecordingTransactionManager transactionManager,
                                                             AiUsageService usageService,
                                                             UserRepository userRepository,
                                                             ChannelMonitor channelMonitor,
                                                             AuditService auditService) {
        var uploadProps = new com.webchat.platformapi.ai.multimodal.MultimodalUploadProperties();
        uploadProps.setEnabled(false);
        return new ChatSseController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                channelMonitor,
                auditService,
                usageService,
                userRepository,
                transactionManager,
                null,
                new ChatSseRequestSupport(),
                new ChatAttachmentPreprocessor(uploadProps, null, null, null, null),
                mock(ChatMcpToolContextService.class),
                mock(ChatSkillContextService.class),
                mock(ChatKnowledgeContextService.class),
                mock(FunctionCallingService.class),
                mock(com.webchat.platformapi.trace.TraceService.class),
                null,
                null,
                "",
                mock(ExecutorService.class)
        );
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        int beginCount;
        int commitCount;
        int rollbackCount;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            beginCount++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            commitCount++;
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            rollbackCount++;
        }
    }

    private static final class FailingNthEventEmitter extends SseEmitter {
        private final int failAtSend;
        private int sendCount;
        private boolean completed;

        private FailingNthEventEmitter(int failAtSend) {
            this.failAtSend = failAtSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            if (sendCount == failAtSend) {
                throw new IOException("emitter send failed");
            }
        }

        @Override
        public synchronized void complete() {
            completed = true;
            super.complete();
        }

        boolean isCompleted() {
            return completed;
        }
    }
}
