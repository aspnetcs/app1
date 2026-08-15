package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyEntity;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.extension.FunctionCallingService;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.credits.CreditAccountEntity;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import com.webchat.platformapi.trace.TraceService;
import com.webchat.platformapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatSseControllerTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private AdapterFactory adapterFactory;
    @Mock
    private ChannelRouter channelRouter;
    @Mock
    private AiCryptoService cryptoService;
    @Mock
    private SsrfGuard ssrfGuard;
    @Mock
    private ChannelMonitor channelMonitor;
    @Mock
    private AuditService auditService;
    @Mock
    private AiUsageService usageService;
    @Mock
    private UserRepository userRepo;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private RolePolicyService rolePolicyService;
    @Mock
    private TraceService traceService;
    @Mock
    private ExecutorService executor;
    @Mock
    private FunctionCallingService functionCallingService;
    @Mock
    private ChatAttachmentPreprocessor attachmentPreprocessor;
    @Mock
    private ChatMcpToolContextService chatMcpToolContextService;
    @Mock
    private ChatSkillContextService chatSkillContextService;
    @Mock
    private ChatKnowledgeContextService chatKnowledgeContextService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(attachmentPreprocessor.process(any(), any()))
                .thenAnswer(invocation -> ChatAttachmentPreprocessor.PreprocessResult.ok(invocation.getArgument(1)));

        ChatSseController controller = new ChatSseController(
                objectMapper,
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                auditService,
                usageService,
                userRepo,
                transactionManager,
                rolePolicyService,
                new ChatSseRequestSupport(),
                attachmentPreprocessor,
                chatMcpToolContextService,
                chatSkillContextService,
                chatKnowledgeContextService,
                functionCallingService,
                traceService,
                null,
                null,
                "",
                executor
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void sseWhenUnauthorized_emitsChatErrorAndCompletes() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/chat/completions/sse")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"v\":1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"ts\":")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"requestId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"traceId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("unauthorized")));
    }

    @Test
    void sseWhenExecutorRejected_emitsServiceBusyErrorAndCompletes() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        // Force a deterministic "service busy" response without touching the heavy streaming code path.
        when(executor.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("rejected"));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/chat/completions/sse")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-5.4-mini",
                                          "askId": "ask-sse-busy",
                                          "abortKey": "abort-sse-busy",
                                          "messages": [
                                            { "role": "user", "content": "hi" }
                                          ]
                                        }
                                        """)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"v\":1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"ts\":")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"requestId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"traceId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"askId\":\"ask-sse-busy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"abortKey\":\"abort-sse-busy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"blocks\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("service busy")));
    }

    @Test
    void sseWhenAttachmentPreprocessingFails_emitsAttachmentErrorAndSkipsStreaming() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        when(attachmentPreprocessor.process(any(), any()))
                .thenReturn(ChatAttachmentPreprocessor.PreprocessResult.error("failed to process attachment: demo.txt"));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/chat/completions/sse")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-5.4-mini",
                                          "messages": [
                                            {
                                              "role": "user",
                                              "content": "read this",
                                              "attachments": [
                                                { "fileId": "file_demo", "kind": "document" }
                                              ]
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("failed to process attachment: demo.txt")));

        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void sseWhenCreditsReservationReturnsNull_emitsReserveFailedErrorAndCompletes() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        CreditsRuntimeService creditsRuntimeService = mock(CreditsRuntimeService.class);
        when(creditsPolicyEvaluator.evaluate(any(), isNull(), anyString(), anyString()))
                .thenReturn(CreditsPolicyEvaluator.PolicyDecision.allow(
                        mock(AiModelMetadataEntity.class),
                        mock(CreditAccountEntity.class)
                ));
        when(creditsRuntimeService.reserve(any(), isNull(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(null);

        ChatSseController controller = new ChatSseController(
                objectMapper,
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                auditService,
                usageService,
                userRepo,
                transactionManager,
                rolePolicyService,
                new ChatSseRequestSupport(),
                attachmentPreprocessor,
                chatMcpToolContextService,
                chatSkillContextService,
                chatKnowledgeContextService,
                functionCallingService,
                traceService,
                creditsPolicyEvaluator,
                creditsRuntimeService,
                "",
                executor
        );
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult result = localMockMvc.perform(
                        post("/api/v1/chat/completions/sse")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-5.4-mini",
                                          "messages": [
                                            { "role": "user", "content": "hi" }
                                          ]
                                        }
                                        """)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        localMockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("credits_reserve_failed")));
    }

    @Test
    void sseWhenCreditsPolicyEvaluationFails_emitsPolicyUnavailableErrorAndCompletes() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        when(creditsPolicyEvaluator.evaluate(any(), isNull(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("policy down"));

        ChatSseController controller = new ChatSseController(
                objectMapper,
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                auditService,
                usageService,
                userRepo,
                transactionManager,
                rolePolicyService,
                new ChatSseRequestSupport(),
                attachmentPreprocessor,
                chatMcpToolContextService,
                chatSkillContextService,
                chatKnowledgeContextService,
                functionCallingService,
                traceService,
                creditsPolicyEvaluator,
                null,
                "",
                executor
        );
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult result = localMockMvc.perform(
                        post("/api/v1/chat/completions/sse")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-5.4-mini",
                                          "messages": [
                                            { "role": "user", "content": "hi" }
                                          ]
                                        }
                                        """)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        localMockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("credits_policy_unavailable")));
    }

    @Test
    void sseWhenFunctionCallingSupported_emitsToolResultAndDone() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(11L);
        channel.setType("openai");
        channel.setBaseUrl("https://example.com");
        AiChannelKeyEntity key = new AiChannelKeyEntity();
        key.setId(22L);
        key.setApiKeyEncrypted("encrypted");
        ChannelSelection selection = new ChannelSelection(channel, key, "gpt-5.4-mini");
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        com.webchat.platformapi.user.UserEntity user = new com.webchat.platformapi.user.UserEntity();
        user.setId(userId);
        user.setTokenQuota(0L);
        user.setTokenUsed(0L);

        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return mock(Future.class);
        });
        when(channelRouter.select(anyString(), any(), any())).thenReturn(selection);
        when(adapterFactory.get("openai")).thenReturn(adapter);
        when(cryptoService.decrypt("encrypted")).thenReturn("plain-key");
        when(userRepo.findByIdAndDeletedAtIsNull(userId)).thenReturn(java.util.Optional.of(user));
        when(userRepo.incrementTokenUsedIfWithinQuota(userId, 12L)).thenReturn(1);
        when(functionCallingService.supports(any(), anyString())).thenReturn(true);
        when(functionCallingService.execute(org.mockito.ArgumentMatchers.eq(channel), org.mockito.ArgumentMatchers.eq("plain-key"), any())).thenReturn(
                new FunctionCallingService.FunctionCallingOutcome(
                        "tool answer",
                        java.util.List.of(java.util.Map.of("toolName", "release_window_lookup", "status", "ok")),
                        5,
                        7,
                        12
                )
        );

        MvcResult result = mockMvc.perform(
                        post("/api/v1/chat/completions/sse")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-5.4-mini",
                                          "messages": [
                                            { "role": "user", "content": "call tool" }
                                          ],
                                          "toolNames": ["mcp_s1_release_window_lookup_db466251"]
                                        }
                                        """)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tool answer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.done")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("release_window_lookup")));
    }

    @Test
    void sseWhenKnowledgeDirectAnswerExists_emitsDirectAnswerWithoutSubmittingUpstreamTask() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        doAnswer(invocation -> {
            Map<String, Object> request = invocation.getArgument(1);
            request.put(ChatKnowledgeContextService.DIRECT_ANSWER_KEY, "Grounded answer: 1. Manual verification first.");
            return null;
        }).when(chatKnowledgeContextService).applyKnowledgeContext(org.mockito.ArgumentMatchers.eq(userId), any());

        MvcResult result = mockMvc.perform(
                        post("/api/v1/chat/completions/sse")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-5.4-mini",
                                          "knowledgeBaseIds": ["kb-1"],
                                          "messages": [
                                            { "role": "user", "content": "Please answer from the selected knowledge base." }
                                          ]
                                        }
                                        """)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Grounded answer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat.done")));

        verify(executor, never()).submit(any(Runnable.class));
    }
}
