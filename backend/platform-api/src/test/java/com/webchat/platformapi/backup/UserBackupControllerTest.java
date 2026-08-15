package com.webchat.platformapi.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.ai.conversation.AiMessageBlockRepository;
import com.webchat.platformapi.ai.conversation.AiMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserBackupControllerTest {

    @Mock
    private AiConversationRepository conversationRepository;
    @Mock
    private AiMessageRepository messageRepository;
    @Mock
    private AiMessageBlockRepository blockRepository;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    private MockMvc mockMvc;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        UserBackupService service = new UserBackupService(conversationRepository, messageRepository, blockRepository);
        UserBackupController controller = new UserBackupController(service, true);
        jwtService = new JwtService(new ObjectMapper(), "12345678901234567890123456789012");
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addFilters(new JwtAuthFilter(jwtService, deviceSessionRepository))
                .build();
    }

    @Test
    void exportRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/backup/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void exportReturnsPayloadEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateHs256(Map.of(
                "userId", userId.toString(),
                "sid", sessionId.toString(),
                "role", "user"
        ), Duration.ofHours(1));

        when(deviceSessionRepository.findActiveSessionForUser(eq(sessionId), eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(activeSession(userId, sessionId)));
        when(conversationRepository.findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(eq(userId)))
                .thenReturn(java.util.List.of());

        mockMvc.perform(
                        get("/api/v1/backup/export")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.kind").value("user_backup"))
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.exportedAt").exists())
                .andExpect(jsonPath("$.data.modules").isArray())
                .andExpect(jsonPath("$.data.data").exists())
                .andExpect(jsonPath("$.data.fileRefs").isArray())
                .andExpect(jsonPath("$.data.meta.conversationCount").value(0));

        verify(conversationRepository).findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(eq(userId));
    }

    private static DeviceSessionEntity activeSession(UUID userId, UUID sessionId) {
        DeviceSessionEntity session = new DeviceSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return session;
    }
}
