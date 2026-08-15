package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GuestChatAccessTest {

    @Mock
    private ChatStreamStarter chatStreamStarter;
    @Mock
    private AuditService auditService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private MultiChatService multiChatService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private AgentService agentService;
    @Mock
    private ChatMcpToolContextService chatMcpToolContextService;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    private MockMvc mockMvc;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        var uploadProps = new com.webchat.platformapi.ai.multimodal.MultimodalUploadProperties();
        uploadProps.setEnabled(false);
        ChatV1Controller controller = new ChatV1Controller(
                chatStreamStarter,
                auditService,
                userGroupService,
                mock(RolePolicyService.class),
                multiChatService,
                redis,
                agentService,
                chatMcpToolContextService,
                mock(ChatSkillContextService.class),
                mock(ChatKnowledgeContextService.class),
                new ObjectMapper(),
                mock(com.webchat.platformapi.trace.TraceService.class),
                new ChatStreamContextRegistry(),
                new ChatAttachmentPreprocessor(uploadProps, null, null, null, null),
                "gpt-4o",
                false,
                false
        );
        jwtService = new JwtService(new ObjectMapper(), "12345678901234567890123456789012");
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addFilters(new JwtAuthFilter(jwtService, deviceSessionRepository))
                .build();
    }

    @Test
    void guestBearerTokenCanAccessChatCompletions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateHs256(Map.of(
                "userId", userId.toString(),
                "sid", sessionId.toString(),
                "role", "guest"
        ), Duration.ofHours(1));
        when(deviceSessionRepository.findActiveSessionForUser(eq(sessionId), eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(activeSession(userId, sessionId)));
        when(chatStreamStarter.startStream(eq(userId), anyString(), anyString(), anyMap(), eq("127.0.0.1"), isNull()))
                .thenReturn(true);

        mockMvc.perform(
                        post("/api/v1/chat/completions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "model": "gpt-4o",
                                          "messages": [
                                            {
                                              "role": "user",
                                              "content": "Hello guest"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requestId").exists());

        verify(deviceSessionRepository).findActiveSessionForUser(eq(sessionId), eq(userId), any(Instant.class));
        verify(chatStreamStarter).startStream(eq(userId), anyString(), anyString(), anyMap(), eq("127.0.0.1"), isNull());
    }

    private static DeviceSessionEntity activeSession(UUID userId, UUID sessionId) {
        DeviceSessionEntity session = new DeviceSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return session;
    }
}
