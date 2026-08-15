package com.webchat.platformapi.ai.conversation;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MessageVersionControllerTest {

    @Mock
    private AiMessageRepository messageRepository;

    @Mock
    private AiConversationRepository conversationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MessageVersionController controller = new MessageVersionController(
                messageRepository,
                conversationRepository,
                true
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void versionsUsesMessagesRouteAndReturnsVersionContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setCreatedAt(Instant.parse("2026-03-23T00:00:00Z"));
        conversation.setUpdatedAt(Instant.parse("2026-03-23T00:01:00Z"));

        AiMessageEntity root = new AiMessageEntity();
        root.setId(rootId);
        root.setConversationId(conversationId);
        root.setRole("assistant");
        root.setContent("Version 1");
        root.setVersion(1);

        AiMessageEntity child = new AiMessageEntity();
        child.setId(childId);
        child.setConversationId(conversationId);
        child.setParentMessageId(rootId);
        child.setRole("assistant");
        child.setContent("Version 2");
        child.setVersion(2);

        when(messageRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByParentMessageIdOrderByVersionAsc(rootId)).thenReturn(List.of(child));

        mockMvc.perform(
                        get("/api/v1/messages/{parentId}/versions", rootId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(rootId.toString()))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[1].id").value(childId.toString()))
                .andExpect(jsonPath("$.data[1].version").value(2));
    }

    @Test
    void versionsRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/messages/{parentId}/versions", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyMessageVersionRouteIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/v1/message/{parentId}/versions", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
