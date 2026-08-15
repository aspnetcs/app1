package com.webchat.platformapi.ai.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GuestConversationAccessTest {

    @Mock
    private AiConversationRepository conversationRepository;
    @Mock
    private AiMessageRepository messageRepository;
    @Mock
    private AiConversationForkService forkService;
    @Mock
    private AuditService auditService;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    private MockMvc mockMvc;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        ConversationController controller = new ConversationController(
                conversationRepository,
                messageRepository,
                forkService,
                auditService,
                true,
                true,
                true
        );
        jwtService = new JwtService(new ObjectMapper(), "12345678901234567890123456789012");
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addFilters(new JwtAuthFilter(jwtService, deviceSessionRepository))
                .build();
    }

    @Test
    void guestBearerTokenCanAccessConversationList() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Guest conversation");
        String token = jwtService.generateHs256(java.util.Map.of(
                "userId", userId.toString(),
                "sid", sessionId.toString(),
                "role", "guest"
        ), Duration.ofHours(1));
        when(deviceSessionRepository.findActiveSessionForUser(eq(sessionId), eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(activeSession(userId, sessionId)));
        when(conversationRepository.findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId))
                .thenReturn(List.of(conversation));
        when(messageRepository.countByConversationIds(anyCollection()))
                .thenReturn(List.of(countRow(conversation.getId(), 1L)));

        mockMvc.perform(
                        get("/api/v1/conversations")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(conversation.getId().toString()))
                .andExpect(jsonPath("$.data[0].title").value("Guest conversation"));

        verify(deviceSessionRepository).findActiveSessionForUser(eq(sessionId), eq(userId), any(Instant.class));
        verify(conversationRepository).findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId);
    }

    private static DeviceSessionEntity activeSession(UUID userId, UUID sessionId) {
        DeviceSessionEntity session = new DeviceSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return session;
    }

    private static AiConversationEntity createConversation(UUID userId, String title) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setModel("gpt-4o");
        entity.setCompareModelsJson("[]");
        entity.setSystemPrompt("Guest");
        entity.setTemporary(false);
        entity.setPinned(false);
        entity.setStarred(false);
        entity.setCreatedAt(Instant.parse("2026-03-23T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-23T00:05:00Z"));
        return entity;
    }

    private static AiMessageRepository.ConversationMessageCount countRow(UUID conversationId, long messageCount) {
        return new AiMessageRepository.ConversationMessageCount() {
            @Override
            public UUID getConversationId() {
                return conversationId;
            }

            @Override
            public long getMessageCount() {
                return messageCount;
            }
        };
    }
}
