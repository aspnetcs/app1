package com.webchat.platformapi.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.credits.CreditAccountEntity;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import com.webchat.platformapi.credits.CreditsSystemConfig;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiGatewayControllerTest {

    @Test
    void gatewaySupportFactoryPassesCreditsRuntimeServiceToQuotaAndProxyServices() throws Exception {
        CreditsRuntimeService creditsRuntimeService = mock(CreditsRuntimeService.class);

        GatewaySupportFactory.Assembly assembly = GatewaySupportFactory.assemble(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                mock(UserRepository.class),
                mock(AiUsageService.class),
                mock(RolePolicyService.class),
                new RecordingTransactionManager(),
                mock(HttpClient.class),
                new DirectExecutorService(),
                creditsRuntimeService
        );

        assertSame(creditsRuntimeService, readField(readField(assembly, "gatewayQuotaService"), "creditsRuntimeService"));
        assertSame(creditsRuntimeService, readField(readField(assembly, "gatewayProxyService"), "creditsRuntimeService"));
    }

    @Test
    void requestUtilsUsesForwardedForOnlyForLoopbackProxy() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 127.0.0.1");

        assertEquals("203.0.113.9", RequestUtils.clientIp(request));
    }

    @Test
    void requestUtilsIgnoresForwardedForFromSiteLocalPeer() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 127.0.0.1");

        assertEquals("192.168.1.10", RequestUtils.clientIp(request));
    }

    @Test
    void userRepositoryAtomicQuotaMethodUsesQuotaGuard() throws Exception {
        Method method = UserRepository.class.getMethod("incrementTokenUsedIfWithinQuota", UUID.class, long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        Query query = method.getAnnotation(Query.class);

        assertNotNull(transactional);
        assertNotNull(query);
        assertTrue(query.value().contains("u.tokenQuota = 0 OR u.tokenUsed + :amount <= u.tokenQuota"));
    }

    @Test
    void chatCompletionsRejectsAnonymousUser() {
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                channelRouter,
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                mock(UserRepository.class),
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                null,
                new RecordingTransactionManager(),
                false,
                mock(HttpClient.class),
                new DirectExecutorService()
        );

        Object result = controller.chatCompletions(null, null, Map.of("model", "gpt-4o"), mock(HttpServletRequest.class));

        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        assertEquals(401, response.getStatusCode().value());
        verify(channelRouter, never()).select(anyString(), anySet(), anySet());
    }

    @Test
    void chatCompletionsReturnsModelNotAllowedWhenCreditsPolicyDeniesRequest() {
        UserRepository userRepository = mock(UserRepository.class);
        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(creditsPolicyEvaluator.evaluate(eq(userId), isNull(), anyString(), anyString()))
                .thenReturn(CreditsPolicyEvaluator.PolicyDecision.deny("model_not_allowed"));

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                mock(RolePolicyService.class),
                new RecordingTransactionManager(),
                false,
                creditsPolicyEvaluator,
                null
        );

        Object result = controller.chatCompletions(userId, "guest", Map.of("model", "gpt-4o"), mock(HttpServletRequest.class));

        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));

        assertEquals(403, response.getStatusCode().value());
        assertEquals("model_not_allowed", error.get("message"));
        assertEquals("model_not_allowed", error.get("code"));
    }

    @Test
    void chatCompletionsReturnsReserveFailedWhenCreditsSnapshotIsMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        CreditsRuntimeService creditsRuntimeService = mock(CreditsRuntimeService.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(creditsPolicyEvaluator.evaluate(eq(userId), isNull(), anyString(), anyString()))
                .thenReturn(CreditsPolicyEvaluator.PolicyDecision.allow(
                        mock(AiModelMetadataEntity.class),
                        mock(CreditAccountEntity.class)
                ));
        when(creditsRuntimeService.reserve(eq(userId), isNull(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(null);

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                mock(RolePolicyService.class),
                new RecordingTransactionManager(),
                false,
                creditsPolicyEvaluator,
                creditsRuntimeService
        );

        Object result = controller.chatCompletions(userId, "user", Map.of("model", "gpt-4o"), mock(HttpServletRequest.class));

        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("credits_reserve_failed", error.get("message"));
        assertEquals("credits_reserve_failed", error.get("code"));
    }

    @Test
    void chatCompletionsReturnsPolicyUnavailableWhenCreditsEvaluationFails() {
        UserRepository userRepository = mock(UserRepository.class);
        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(creditsPolicyEvaluator.evaluate(eq(userId), isNull(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("policy down"));

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                mock(RolePolicyService.class),
                new RecordingTransactionManager(),
                false,
                creditsPolicyEvaluator,
                null
        );

        Object result = controller.chatCompletions(userId, "user", Map.of("model", "gpt-4o"), mock(HttpServletRequest.class));

        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("credits_policy_unavailable", error.get("message"));
        assertEquals("credits_policy_unavailable", error.get("code"));
    }

    @Test
    void chatCompletionsBypassesRoleWhitelistWhenFreeModeIsEnabled() {
        UserRepository userRepository = mock(UserRepository.class);
        UserGroupService userGroupService = mock(UserGroupService.class);
        RolePolicyService rolePolicyService = mock(RolePolicyService.class);
        GatewayModelsFacade gatewayModelsFacade = mock(GatewayModelsFacade.class);
        GatewayQuotaService gatewayQuotaService = mock(GatewayQuotaService.class);
        GatewayProxyService gatewayProxyService = mock(GatewayProxyService.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(rolePolicyService.resolveAllowedModels(eq(userId), eq("guest"))).thenReturn(java.util.Set.of("gpt-4"));
        ResponseEntity<?> gatewayResponse = ResponseEntity.ok(Map.of("id", "chatcmpl_free_mode"));
        org.mockito.Mockito.doReturn(gatewayResponse)
                .when(gatewayProxyService)
                .nonStreamChat(eq(Map.of("model", "gpt-4o", "stream", false)), eq("gpt-4o"), eq(userId));

        OpenAiGatewayController controller = new OpenAiGatewayController(
                userRepository,
                userGroupService,
                rolePolicyService,
                new GatewaySupportFactory.Assembly(
                        gatewayModelsFacade,
                        gatewayQuotaService,
                        gatewayProxyService,
                        new DirectExecutorService()
                ),
                false,
                null,
                null
        );
        CreditsSystemConfig creditsSystemConfig = mock(CreditsSystemConfig.class);
        when(creditsSystemConfig.isFreeModeEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(controller, "creditsSystemConfig", creditsSystemConfig);

        Object result = controller.chatCompletions(
                userId,
                "guest",
                Map.of("model", "gpt-4o", "stream", false),
                mock(HttpServletRequest.class)
        );

        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        assertEquals(200, response.getStatusCode().value());
        verify(gatewayProxyService).nonStreamChat(eq(Map.of("model", "gpt-4o", "stream", false)), eq("gpt-4o"), eq(userId));
    }

    @Test
    void modelsRejectAnonymousUser() {
        AiChannelRepository channelRepository = mock(AiChannelRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                channelRepository,
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                null,
                new RecordingTransactionManager(),
                false,
                mock(HttpClient.class),
                new DirectExecutorService()
        );

        ResponseEntity<Map<String, Object>> response = controller.models(null, null);

        assertEquals(401, response.getStatusCode().value());
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(channelRepository, never()).findByEnabledTrueAndStatus(anyInt());
    }

    @Test
    void modelsRejectMissingUser() {
        AiChannelRepository channelRepository = mock(AiChannelRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                channelRepository,
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                null,
                new RecordingTransactionManager(),
                false,
                mock(HttpClient.class),
                new DirectExecutorService()
        );

        ResponseEntity<Map<String, Object>> response = controller.models(userId, null);

        assertEquals(401, response.getStatusCode().value());
        verify(channelRepository, never()).findByEnabledTrueAndStatus(anyInt());
    }

    @Test
    void modelsFailClosedWhenGroupProfileResolutionFails() {
        AiChannelRepository channelRepository = mock(AiChannelRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        RolePolicyService rolePolicyService = mock(RolePolicyService.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(channelRepository.findByEnabledTrueAndStatus(anyInt())).thenReturn(List.of(channel));
        when(rolePolicyService.resolveAllowedModels(eq(userId), any())).thenThrow(new IllegalStateException("policy service down"));

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                channelRepository,
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                rolePolicyService,
                new RecordingTransactionManager(),
                true,
                mock(HttpClient.class),
                new DirectExecutorService()
        );

        ResponseEntity<Map<String, Object>> response = controller.models(userId, null);
        Map<?, ?> error = assertInstanceOf(Map.class, response.getBody().get("error"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("policy unavailable", error.get("message"));
        verify(rolePolicyService).resolveAllowedModels(eq(userId), any());
    }

    @Test
    void chatCompletionsFailClosedWhenGroupPolicyCheckThrows() {
        UserRepository userRepository = mock(UserRepository.class);
        RolePolicyService rolePolicyService = mock(RolePolicyService.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(100);
        user.setTokenUsed(0);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(rolePolicyService.resolveAllowedModels(eq(userId), any())).thenThrow(new IllegalStateException("policy service down"));

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                channelRouter,
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                rolePolicyService,
                new RecordingTransactionManager(),
                true,
                mock(HttpClient.class),
                new DirectExecutorService()
        );

        Object result = controller.chatCompletions(userId, null,
                Map.of("model", "gpt-4o", "stream", false),
                mock(HttpServletRequest.class));

        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("policy unavailable", error.get("message"));
        verify(rolePolicyService).resolveAllowedModels(eq(userId), any());
        verify(channelRouter, never()).select(anyString(), anySet(), anySet());
    }

    @Test
    void nonStreamChatPersistsUsageAndDeductsQuotaBeforeReturningSuccess() throws Exception {
        AdapterFactory adapterFactory = mock(AdapterFactory.class);
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        SsrfGuard ssrfGuard = mock(SsrfGuard.class);
        ChannelMonitor channelMonitor = mock(ChannelMonitor.class);
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        HttpClient httpClient = mock(HttpClient.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(100);
        user.setTokenUsed(10);

        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(11L);
        channel.setType("openai");
        channel.setBaseUrl("https://upstream.example");

        AiChannelKeyEntity key = new AiChannelKeyEntity();
        key.setId(22L);
        key.setApiKeyEncrypted("encrypted-key");

        ChannelSelection selection = new ChannelSelection(channel, key, "gpt-4o");
        String responseBody = """
                {"id":"chatcmpl_1","choices":[],"usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
                """;

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 18)).thenReturn(1);
        when(channelRouter.select(eq("gpt-4o"), anySet(), anySet())).thenReturn(selection);
        when(adapterFactory.get("openai")).thenReturn(adapter);
        when(cryptoService.decrypt("encrypted-key")).thenReturn("plain-key");
        when(adapter.buildChatRequest(anyMap(), eq(channel), eq("plain-key"), eq(false)))
                .thenReturn(HttpRequest.newBuilder(URI.create("https://upstream.example/chat")).build());
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(new StaticHttpResponse<>(200, responseBody));

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                mock(AiChannelRepository.class),
                userRepository,
                usageService,
                mock(UserGroupService.class),
                null,
                transactionManager,
                false,
                httpClient,
                new DirectExecutorService()
        );

        Object result = controller.chatCompletions(userId, null,
                Map.of("model", "gpt-4o", "stream", false),
                mock(HttpServletRequest.class));

        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(eq(userId), eq(11L), eq("gateway"), eq("gpt-4o"),
                eq(11), eq(7), eq(0L), eq(true), anyString());
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 18);
    }

    @Test
    void persistUsageAndDeductQuotaRollsBackWhenAtomicQuotaUpdateRejects() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();

        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 18)).thenReturn(0);

        OpenAiGatewayController controller = new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                userRepository,
                usageService,
                mock(UserGroupService.class),
                null,
                transactionManager,
                false,
                mock(HttpClient.class),
                new DirectExecutorService()
        );

        assertThrows(IllegalStateException.class, () ->
                controller.persistUsageAndDeductQuota(userId, 11L, "gpt-4o", 11, 7, 18, "req-quota"));

        assertEquals(1, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(1, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(eq(userId), eq(11L), eq("gateway"), eq("gpt-4o"),
                eq(11), eq(7), eq(0L), eq(true), eq("req-quota"));
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 18);
    }

    @Test
    void streamChatReturnsBusyErrorWhenExecutorIsSaturated() {
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(100);
        user.setTokenUsed(0);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 100)).thenReturn(1);
        when(userRepository.decrementTokenUsed(userId, 100)).thenReturn(1);

        CapturingSseEmitter emitter = new CapturingSseEmitter();
        TestableOpenAiGatewayController controller = new TestableOpenAiGatewayController(
                emitter,
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                userRepository,
                mock(AiUsageService.class),
                mock(UserGroupService.class),
                new RecordingTransactionManager(),
                false,
                mock(HttpClient.class),
                new RejectingExecutorService()
        );

        Object result = controller.chatCompletions(userId, null,
                Map.of("model", "gpt-4o", "stream", true),
                mock(HttpServletRequest.class));

        SseEmitter returned = assertInstanceOf(SseEmitter.class, result);
        assertEquals(emitter, returned);
        assertTrue(emitter.isCompleted());
        assertEquals(1, emitter.payloads.size());
        assertTrue(emitter.payloads.get(0).contains("service busy"));
        verify(userRepository).decrementTokenUsed(userId, 100);
    }

    @Test
    void streamChatReservesRemainingQuotaAndSetsDefaultLimitWhenMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(120);
        user.setTokenUsed(75);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 45)).thenReturn(1);

        OpenAiGatewayController controller = createController(transactionManager, userRepository, usageService);

        long reserved = controller.reserveStreamingQuota(userId, requestBody);

        assertEquals(45L, reserved);
        assertEquals(45, requestBody.get("max_tokens"));
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 45);
    }

    @Test
    void streamChatRejectsBeforeOutputWhenQuotaReserveFails() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(100);
        user.setTokenUsed(90);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 10)).thenReturn(0);

        OpenAiGatewayController controller = createController(transactionManager, userRepository, usageService);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                controller.reserveStreamingQuota(userId, new HashMap<>()));

        assertEquals("Token quota exceeded", error.getMessage());
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository).incrementTokenUsedIfWithinQuota(userId, 10);
    }

    @Test
    void streamChatProtectsAgainstFullReservationWhenFinalizeFailsAfterPartialOutput() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();

        doThrow(new IllegalStateException("log fail"))
                .when(usageService).logUsageStrict(userId, 11L, "gateway", "gpt-4o", 10, 20, 0L, true, "req-gw-partial");
        when(userRepository.decrementTokenUsed(userId, 70)).thenReturn(1);

        OpenAiGatewayController controller = createController(transactionManager, userRepository, usageService);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                controller.finalizeReservedQuotaAfterStream(userId, 100, 11L, "gpt-4o",
                        10, 20, 30, "req-gw-partial", true, true));

        assertEquals("log fail", error.getMessage());
        assertEquals(2, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(1, transactionManager.rollbackCount);
        verify(usageService).logUsageStrict(userId, 11L, "gateway", "gpt-4o", 10, 20, 0L, true, "req-gw-partial");
        verify(userRepository).decrementTokenUsed(userId, 70);
    }

    @Test
    void streamChatRethrowsWhenPartialRefundFailsWithoutFullReservationFallback() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();

        when(userRepository.decrementTokenUsed(userId, 70)).thenReturn(0);

        OpenAiGatewayController controller = createController(transactionManager, userRepository, usageService);

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
    void streamChatDoesNotRefundWhenStreamFailsAfterPartialOutputEvenWithUsageSnapshot() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();

        OpenAiGatewayController controller = createController(transactionManager, userRepository, usageService);

        controller.protectReservedQuotaOnStreamFailure(userId, 100, 10, 20, 30, true);

        assertEquals(0, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository, never()).decrementTokenUsed(any(), anyLong());
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
    }

    @Test
    void streamChatDoesNotRefundWhenStreamFailsAfterPartialOutputAndUsageIsUnknown() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();

        OpenAiGatewayController controller = createController(transactionManager, userRepository, usageService);

        controller.protectReservedQuotaOnStreamFailure(userId, 100, 0, 0, 0, true);

        assertEquals(0, transactionManager.beginCount);
        assertEquals(0, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
        verify(userRepository, never()).decrementTokenUsed(any(), anyLong());
    }

    @Test
    void streamChatReleasesReservationWhenFirstChunkDeliveryFails() throws Exception {
        AdapterFactory adapterFactory = mock(AdapterFactory.class);
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        SsrfGuard ssrfGuard = mock(SsrfGuard.class);
        ChannelMonitor channelMonitor = mock(ChannelMonitor.class);
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        HttpClient httpClient = mock(HttpClient.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UUID userId = UUID.randomUUID();

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

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 20)).thenReturn(1);
        when(userRepository.decrementTokenUsed(userId, 20)).thenReturn(1);
        when(channelRouter.select(eq("gpt-4o"), anySet(), anySet()))
                .thenReturn(selection)
                .thenThrow(new NoChannelException("no channel"));
        when(adapterFactory.get("openai")).thenReturn(adapter);
        when(cryptoService.decrypt("encrypted-key")).thenReturn("plain-key");
        when(adapter.buildChatRequest(anyMap(), eq(channel), eq("plain-key"), eq(true)))
                .thenReturn(HttpRequest.newBuilder(URI.create("https://upstream.example/chat")).build());
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(new StaticHttpResponse<>(200, new ByteArrayInputStream("line-1\n".getBytes(StandardCharsets.UTF_8))));
        when(adapter.parseStreamLine("line-1")).thenReturn(StreamChunk.delta("hello"));

        FailingFirstChunkEmitter emitter = new FailingFirstChunkEmitter();
        TestableOpenAiGatewayController controller = new TestableOpenAiGatewayController(
                emitter,
                new ObjectMapper(),
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                mock(AiChannelRepository.class),
                userRepository,
                usageService,
                mock(UserGroupService.class),
                transactionManager,
                false,
                httpClient,
                new DirectExecutorService()
        );

        Object result = controller.chatCompletions(userId, null,
                Map.of("model", "gpt-4o", "stream", true),
                mock(HttpServletRequest.class));

        assertEquals(emitter, result);
        assertTrue(emitter.isCompleted());
        verify(userRepository).decrementTokenUsed(userId, 20);
        verify(usageService, never()).logUsageStrict(any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean(), any());
    }

    private static OpenAiGatewayController createController(RecordingTransactionManager transactionManager,
                                                            UserRepository userRepository,
                                                            AiUsageService usageService) {
        return new OpenAiGatewayController(
                new ObjectMapper(),
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(ChannelMonitor.class),
                mock(AiChannelRepository.class),
                userRepository,
                usageService,
                mock(UserGroupService.class),
                null,
                transactionManager,
                false,
                mock(HttpClient.class),
                new DirectExecutorService()
        );
    }

    private static final class TestableOpenAiGatewayController extends OpenAiGatewayController {
        private final SseEmitter emitter;

        private TestableOpenAiGatewayController(
                SseEmitter emitter,
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
                PlatformTransactionManager transactionManager,
                boolean userGroupsEnabled,
                HttpClient httpClient,
                ExecutorService executor
        ) {
            super(objectMapper, adapterFactory, channelRouter, cryptoService, ssrfGuard, channelMonitor,
                    channelRepo, userRepo, usageService, userGroupService, null,
                    transactionManager, userGroupsEnabled, httpClient, executor);
            this.emitter = emitter;
        }

        @Override
        SseEmitter createEmitter(long timeoutMs) {
            return emitter;
        }
    }

    private static final class CapturingSseEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();
        private boolean completed;

        @Override
        public void send(Object object) throws IOException {
            payloads.add(String.valueOf(object));
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            payloads.add(String.valueOf(builder));
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

    private static final class FailingFirstChunkEmitter extends SseEmitter {
        private boolean failedChunk;
        private boolean completed;

        @Override
        public void send(Object object) throws IOException {
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (!failedChunk) {
                failedChunk = true;
                throw new IOException("chunk delivery failed");
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

    private static final class RejectingExecutorService extends AbstractExecutorService {
        private final AtomicBoolean shutdown = new AtomicBoolean(false);

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return shutdown.get();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            throw new java.util.concurrent.RejectedExecutionException("busy");
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private final AtomicBoolean shutdown = new AtomicBoolean(false);

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return shutdown.get();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static final class StaticHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final T body;

        private StaticHttpResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://upstream.example/chat")).build();
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (left, right) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://upstream.example/chat");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
