package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.filter.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatV1Controller.class)
@TestPropertySource(properties = {
        // Keep tests deterministic: missing-model should fail unless the client explicitly provides one.
        "ai.default-model="
})
class ChatV1ControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatStreamStarter chatStreamStarter;

    @MockBean
    private AuditService auditService;

    @MockBean
    private UserGroupService userGroupService;

    @MockBean
    private RolePolicyService rolePolicyService;

    @MockBean
    private MultiChatService multiChatService;

    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    @MockBean
    private AgentService agentService;
    @MockBean
    private ChatMcpToolContextService chatMcpToolContextService;
    @MockBean
    private ChatSkillContextService chatSkillContextService;
    @MockBean
    private ChatKnowledgeContextService chatKnowledgeContextService;

    @MockBean
    private ChatStreamContextRegistry streamContextRegistry;

    @MockBean
    private ChatAttachmentPreprocessor attachmentPreprocessor;

    @BeforeEach
    void stubGroupProfileDefaults() {
        when(userGroupService.resolveProfile(any(UUID.class)))
                .thenReturn(new UserGroupService.GroupProfile(
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        null
                ));
        when(attachmentPreprocessor.process(any(), any()))
                .thenAnswer(inv -> ChatAttachmentPreprocessor.PreprocessResult.ok(inv.getArgument(1)));
    }

    @Test
    void completions_requires_auth() throws Exception {
        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("user not authenticated"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void completions_missing_model_returns_param_missing() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(any(UUID.class), any())).thenReturn(Set.of());

        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"messages\":[]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("missing model"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(chatStreamStarter, never()).startStream(any(), any(), any(), any(), any(), any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void completions_rejects_model_not_allowed_by_role_policy() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(any(UUID.class), any())).thenReturn(Set.of("gpt-4"));

        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"model\":\"gpt-5.4-mini\",\"messages\":[]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.MODEL_NOT_ALLOWED))
                .andExpect(jsonPath("$.message").value("model_not_allowed"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(chatStreamStarter, never()).startStream(any(), any(), any(), any(), any(), any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void completions_service_busy_returns_rate_limit() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(any(UUID.class), any())).thenReturn(Set.of());
        when(chatStreamStarter.startStream(any(), any(), any(), any(), any(), any())).thenReturn(false);

        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"model\":\"gpt-5.4-mini\",\"messages\":[]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.RATE_LIMIT))
                .andExpect(jsonPath("$.message").value("service is busy"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void completions_ok_returns_request_id_and_audits() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(any(UUID.class), any())).thenReturn(Set.of());
        when(chatStreamStarter.startStream(any(), any(), any(), any(), any(), any())).thenReturn(true);

        String requestId = "req_test_1";
        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                                .requestAttr(RequestIdFilter.ATTR_KEY, requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"model\":\"gpt-5.4-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SUCCESS))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.requestId").value(requestId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(eq(userId), eq("chat.request"), detailCaptor.capture(), any(), any());
        Map<String, Object> detail = detailCaptor.getValue();
        assertThat(detail.get("requestId")).isEqualTo(requestId);
        assertThat(detail.get("model")).isEqualTo("gpt-5.4-mini");
    }

    @Test
    void completionsMulti_returnsParamInvalidWhenAttachmentPreprocessingFails() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatService.isParallelEnabled()).thenReturn(true);
        when(multiChatService.getParallelMaxModels()).thenReturn(3);
        when(userGroupService.isFeatureAllowed(eq(userId), eq("multi_chat"))).thenReturn(true);
        when(attachmentPreprocessor.process(any(), any()))
                .thenReturn(ChatAttachmentPreprocessor.PreprocessResult.error("failed to process attachment: demo.pdf"));

        mockMvc.perform(
                        post("/api/v1/chat/completions/multi")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "models": ["gpt-5.4-mini", "gpt-4o-mini"],
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_INVALID))
                .andExpect(jsonPath("$.message").value("failed to process attachment: demo.pdf"));
    }

    @Test
    void multi_agent_discussion_config_requires_auth() throws Exception {
        mockMvc.perform(get("/api/v1/chat/multi-agent-discussion/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("user not authenticated"));
    }

    @Test
    void multi_agent_discussion_config_returns_feature_flags() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatService.isMultiAgentDiscussionEnabled()).thenReturn(true);
        when(multiChatService.getMultiAgentDiscussionMaxAgents()).thenReturn(4);
        when(multiChatService.getMultiAgentDiscussionMaxRounds()).thenReturn(20);
        when(userGroupService.isFeatureAllowed(eq(userId), eq("multi_agent_discussion"))).thenReturn(true);

        mockMvc.perform(
                        get("/api/v1/chat/multi-agent-discussion/config")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SUCCESS))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxAgents").value(4))
                .andExpect(jsonPath("$.data.maxRounds").value(20));
    }
}
