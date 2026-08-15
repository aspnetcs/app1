package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.filter.RequestIdFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatV1ControllerTest {

    @Mock
    private ChatStreamStarter chatStreamStarter;

    @Mock
    private AuditService auditService;

    @Mock
    private UserGroupService userGroupService;

    @Mock
    private RolePolicyService rolePolicyService;

    @Mock
    private MultiChatService multiChatService;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private AgentService agentService;
    @Mock
    private ChatMcpToolContextService chatMcpToolContextService;
    @Mock
    private ChatSkillContextService chatSkillContextService;
    @Mock
    private ChatKnowledgeContextService chatKnowledgeContextService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Create a real ChatAttachmentPreprocessor with feature disabled –
        // when disabled it just strips attachments and returns the request as-is.
        var uploadProps = new com.webchat.platformapi.ai.multimodal.MultimodalUploadProperties();
        uploadProps.setEnabled(false);
        ChatAttachmentPreprocessor preprocessor = new ChatAttachmentPreprocessor(
                uploadProps, null, null, null, null);

        ChatV1Controller controller = new ChatV1Controller(
                chatStreamStarter,
                auditService,
                userGroupService,
                rolePolicyService,
                multiChatService,
                redis,
                agentService,
                chatMcpToolContextService,
                chatSkillContextService,
                chatKnowledgeContextService,
                new ObjectMapper(),
                mock(com.webchat.platformapi.trace.TraceService.class),
                new ChatStreamContextRegistry(),
                preprocessor,
                "gpt-4o",
                false,
                false
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createUsesChatCompletionsRouteAndReturnsRequestId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(chatStreamStarter.startStream(eq(userId), anyString(), anyString(), anyMap(), eq("127.0.0.1"), isNull())).thenReturn(true);
        String traceId = "trace_test_1";

        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(RequestIdFilter.TRACE_ATTR_KEY, traceId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-4o",
                                          "messages": [
                                            {
                                              "role": "user",
                                              "content": "Hello"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requestId").value(org.hamcrest.Matchers.startsWith("req_")));

        verify(chatStreamStarter).startStream(
                eq(userId),
                anyString(),
                eq(traceId),
                argThat(body -> Boolean.TRUE.equals(body.get("stream"))
                        && "gpt-4o".equals(body.get("model"))
                        && "user".equals(body.get("__userRole"))),
                eq("127.0.0.1"),
                isNull()
        );
        verify(chatMcpToolContextService).applySavedMcpToolNames(eq(userId), anyMap());
        verify(chatKnowledgeContextService).applyKnowledgeContext(eq(userId), anyMap());
    }

    @Test
    void createMultiUsesExactRouteAndReturnsRoundContract() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatService.isParallelEnabled()).thenReturn(true);
        when(multiChatService.getParallelMaxModels()).thenReturn(3);
        when(multiChatService.start(
                eq(userId),
                anyString(),
                anyMap(),
                eq(List.of("gpt-4o", "gpt-4o-mini")),
                eq("127.0.0.1"),
                isNull()
        )).thenReturn(new MultiChatService.MultiChatResult(
                "round-1",
                List.of(
                        Map.of("requestId", "req_a", "model", "gpt-4o", "roundId", "round-1"),
                        Map.of("requestId", "req_b", "model", "gpt-4o-mini", "roundId", "round-1")
                )
        ));

        mockMvc.perform(
                        post("/api/v1/chat/completions/multi")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "models": ["gpt-4o", "gpt-4o-mini"],
                                          "messages": [
                                            {
                                              "role": "user",
                                              "content": "Compare this"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roundId").value("round-1"))
                .andExpect(jsonPath("$.data.items[0].model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.items[1].model").value("gpt-4o-mini"));

        verify(multiChatService).start(
                eq(userId),
                anyString(),
                argThat(body -> Boolean.TRUE.equals(body.get("stream"))
                        && !body.containsKey("models")
                        && "user".equals(body.get("__userRole"))),
                eq(List.of("gpt-4o", "gpt-4o-mini")),
                eq("127.0.0.1"),
                isNull()
        );
        verify(chatMcpToolContextService).applySavedMcpToolNames(eq(userId), anyMap());
        verify(chatKnowledgeContextService).applyKnowledgeContext(eq(userId), anyMap());
    }

    @Test
    void unauthenticatedChatRoutesAreRejected() throws Exception {
        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .contentType(APPLICATION_JSON)
                                .content("{\"messages\":[]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        post("/api/v1/chat/completions/multi")
                                .contentType(APPLICATION_JSON)
                                .content("{\"models\":[\"gpt-4o\",\"gpt-4o-mini\"]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/chat/multi-agent-discussion/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        post("/api/v1/chat/multi-agent-discussion/start")
                                .contentType(APPLICATION_JSON)
                                .content("{\"agentIds\":[]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        post("/api/v1/chat/multi-agent-discussion/next")
                                .contentType(APPLICATION_JSON)
                                .content("{\"sessionId\":\"s-1\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void createMultiRejectsWhenRuntimeConfigDisablesParallelMode() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatService.isParallelEnabled()).thenReturn(false);

        mockMvc.perform(
                        post("/api/v1/chat/completions/multi")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "models": ["gpt-4o", "gpt-4o-mini"],
                                          "messages": [{"role": "user", "content": "Compare this"}]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SERVER_ERROR))
                .andExpect(jsonPath("$.message").value("multi chat is disabled"));
    }

    @Test
    void legacyChatRoutesAreNotMapped() throws Exception {
        mockMvc.perform(post("/api/v1/chat/completion"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/chat/multi"))
                .andExpect(status().isNotFound());
        
        mockMvc.perform(get("/api/v1/roleplay/config"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/roleplay/start"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/roleplay/next"))
                .andExpect(status().isNotFound());
    }

    @Test
    void multiAgentDiscussionConfigReturnsUnifiedContract() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatService.isMultiAgentDiscussionEnabled()).thenReturn(true);
        when(multiChatService.getMultiAgentDiscussionMaxAgents()).thenReturn(4);
        when(multiChatService.getMultiAgentDiscussionMaxRounds()).thenReturn(12);

        mockMvc.perform(
                        get("/api/v1/chat/multi-agent-discussion/config")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxAgents").value(4))
                .andExpect(jsonPath("$.data.maxRounds").value(12))
                .andExpect(jsonPath("$.data.maxRoles").doesNotExist());
    }

    @Test
    void multiAgentDiscussionStartUsesUnifiedRequestAndResponseShape() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentA = UUID.randomUUID();
        UUID agentB = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(eq(userId), eq("user"))).thenReturn(Set.of());
        when(multiChatService.isMultiAgentDiscussionEnabled()).thenReturn(true);
        when(multiChatService.startMultiAgentDiscussion(eq(userId), eq(List.of(agentA, agentB)), eq("Debate the roadmap"), eq(List.of())))
                .thenReturn(new MultiChatService.DiscussionSession(
                        "discussion-1",
                        "Debate the roadmap",
                        List.of(
                                new MultiChatService.DiscussionAgent(agentA, "Architect", "a.png", "sys-a", "gpt-4o"),
                                new MultiChatService.DiscussionAgent(agentB, "Reviewer", "b.png", "sys-b", "gpt-4o-mini")
                        ),
                        0,
                        8,
                        List.of()
                ));

        mockMvc.perform(
                        post("/api/v1/chat/multi-agent-discussion/start")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "agentIds": ["%s", "%s"],
                                          "topic": "Debate the roadmap"
                                        }
                                        """.formatted(agentA, agentB))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value("discussion-1"))
                .andExpect(jsonPath("$.data.maxRounds").value(8))
                .andExpect(jsonPath("$.data.agents[0].agentId").value(agentA.toString()))
                .andExpect(jsonPath("$.data.agents[0].name").value("Architect"))
                .andExpect(jsonPath("$.data.agents[1].agentId").value(agentB.toString()))
                .andExpect(jsonPath("$.data.roles").doesNotExist());
    }

    @Test
    void multiAgentDiscussionStartRejectsAgentsOutsideRolePolicy() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentA = UUID.randomUUID();
        UUID agentB = UUID.randomUUID();
        when(multiChatService.isMultiAgentDiscussionEnabled()).thenReturn(true);
        when(rolePolicyService.resolveAllowedModels(eq(userId), eq("guest"))).thenReturn(Set.of("gpt-4o"));
        when(agentService.loadAgents(eq(List.of(agentA, agentB)))).thenReturn(List.of(
                agent(agentA, "Architect", "gpt-4o"),
                agent(agentB, "Reviewer", "gpt-4o-mini")
        ));

        mockMvc.perform(
                        post("/api/v1/chat/multi-agent-discussion/start")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "guest")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "agentIds": ["%s", "%s"],
                                          "topic": "Debate the roadmap"
                                        }
                                        """.formatted(agentA, agentB))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.MODEL_NOT_ALLOWED))
                .andExpect(jsonPath("$.message").value("model_not_allowed"));

        verify(multiChatService, never()).startMultiAgentDiscussion(eq(userId), eq(List.of(agentA, agentB)), anyString(), anyList());
    }

    @Test
    void multiAgentDiscussionNextUsesAgentIndexContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentA = UUID.randomUUID();
        when(multiChatService.isMultiAgentDiscussionEnabled()).thenReturn(true);
        when(multiChatService.getDiscussionSessionForUser("discussion-1", userId))
                .thenReturn(new MultiChatService.DiscussionSession(
                        "discussion-1",
                        "Debate the roadmap",
                        List.of(new MultiChatService.DiscussionAgent(agentA, "Architect", "a.png", "sys-a", "gpt-4o")),
                        0,
                        8,
                        List.of()
                ));
        when(multiChatService.buildNextDiscussionTurn(eq(userId), anyString(), org.mockito.ArgumentMatchers.any(), eq(0), anyList()))
                .thenReturn(new ChatStreamTask("req_discussion_1", "trace_discussion_1", Map.of("model", "gpt-4o")));
        when(chatStreamStarter.startStreams(eq(userId), anyList(), eq("127.0.0.1"), eq("unknown"))).thenReturn(true);

        mockMvc.perform(
                        post("/api/v1/chat/multi-agent-discussion/next")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "sessionId": "discussion-1",
                                          "agentIndex": 0,
                                          "history": []
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requestId").value("req_discussion_1"))
                .andExpect(jsonPath("$.data.agentIndex").value(0))
                .andExpect(jsonPath("$.data.agentName").value("Architect"))
                .andExpect(jsonPath("$.data.agentAvatar").value("a.png"))
                .andExpect(jsonPath("$.data.roleIndex").doesNotExist());

        verify(multiChatService, never()).completeDiscussionRound("discussion-1", userId);
        verify(chatStreamStarter).startStreams(
                eq(userId),
                argThat(tasks -> tasks != null
                        && tasks.size() == 1
                        && "user".equals(tasks.get(0).requestBody().get("__userRole"))),
                eq("127.0.0.1"),
                eq("unknown")
        );
    }

    @Test
    void multiAgentDiscussionNextAcceptsStringAgentIndex() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentA = UUID.randomUUID();
        when(multiChatService.isMultiAgentDiscussionEnabled()).thenReturn(true);
        when(multiChatService.getDiscussionSessionForUser("discussion-2", userId))
                .thenReturn(new MultiChatService.DiscussionSession(
                        "discussion-2",
                        "Refine the launch plan",
                        List.of(new MultiChatService.DiscussionAgent(agentA, "Architect", "a.png", "sys-a", "gpt-4o")),
                        0,
                        8,
                        List.of()
                ));
        when(multiChatService.buildNextDiscussionTurn(
                eq(userId),
                anyString(),
                org.mockito.ArgumentMatchers.any(),
                eq(0),
                argThat(history -> history != null
                        && history.size() == 1
                        && "user".equals(history.get(0).get("role"))
                        && "Prior context".equals(history.get(0).get("content")))
        )).thenReturn(new ChatStreamTask("req_discussion_2", "trace_discussion_2", Map.of("model", "gpt-4o")));
        when(chatStreamStarter.startStreams(eq(userId), anyList(), eq("127.0.0.1"), eq("unknown"))).thenReturn(true);

        mockMvc.perform(
                        post("/api/v1/chat/multi-agent-discussion/next")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "sessionId": "discussion-2",
                                          "agentIndex": "0",
                                          "history": [
                                            {
                                              "role": "user",
                                              "content": "Prior context"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requestId").value("req_discussion_2"))
                .andExpect(jsonPath("$.data.agentIndex").value(0));
    }

    @Test
    void createAllowsDisallowedModelWhenFreeModeIsEnabled() throws Exception {
        UUID userId = UUID.randomUUID();
        when(chatStreamStarter.startStream(eq(userId), anyString(), anyString(), anyMap(), eq("127.0.0.1"), isNull()))
                .thenReturn(true);

        var uploadProps = new com.webchat.platformapi.ai.multimodal.MultimodalUploadProperties();
        uploadProps.setEnabled(false);
        ChatAttachmentPreprocessor preprocessor = new ChatAttachmentPreprocessor(
                uploadProps, null, null, null, null);
        ChatV1Controller controller = new ChatV1Controller(
                chatStreamStarter,
                auditService,
                userGroupService,
                rolePolicyService,
                multiChatService,
                redis,
                agentService,
                chatMcpToolContextService,
                chatSkillContextService,
                chatKnowledgeContextService,
                new ObjectMapper(),
                mock(com.webchat.platformapi.trace.TraceService.class),
                new ChatStreamContextRegistry(),
                preprocessor,
                "gpt-4o",
                false,
                false
        );
        com.webchat.platformapi.credits.CreditsSystemConfig creditsSystemConfig =
                mock(com.webchat.platformapi.credits.CreditsSystemConfig.class);
        when(creditsSystemConfig.isFreeModeEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(controller, "creditsSystemConfig", creditsSystemConfig);
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        localMockMvc.perform(
                        post("/api/v1/chat/completions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "guest")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-5.4-mini",
                                          "messages": [
                                            {
                                              "role": "user",
                                              "content": "Hello"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(chatStreamStarter).startStream(
                eq(userId),
                anyString(),
                anyString(),
                argThat(body -> "guest".equals(body.get("__userRole"))),
                eq("127.0.0.1"),
                isNull()
        );
    }

    private static AgentEntity agent(UUID id, String name, String modelId) {
        AgentEntity agent = new AgentEntity();
        agent.setId(id);
        agent.setName(name);
        agent.setModelId(modelId);
        return agent;
    }
}
