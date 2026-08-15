package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.ai.conversation.AiConversationEntity;
import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.ai.conversation.AiMessageEntity;
import com.webchat.platformapi.ai.conversation.AiMessageRepository;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MessageVersionAdminControllerTest {

    @Mock
    private AiMessageRepository messageRepository;
    @Mock
    private AiConversationRepository conversationRepository;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MessageVersionAdminController(messageRepository, conversationRepository, true)
        ).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void versionConfigRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/messages/version-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void restoreVersionCreatesNewLatestVersion() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID restoredId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        AiMessageEntity parent = message(parentId, conversationId, 1, "first version");
        AiMessageEntity candidate = message(UUID.randomUUID(), conversationId, 2, "second version");
        candidate.setParentMessageId(parentId);
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(conversationId);
        conversation.setUpdatedAt(Instant.parse("2026-03-24T09:00:00Z"));

        when(messageRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(messageRepository.findByParentMessageIdOrderByVersionAsc(parentId)).thenReturn(List.of(candidate));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(AiMessageEntity.class))).thenAnswer(invocation -> {
            AiMessageEntity saved = invocation.getArgument(0);
            saved.setId(restoredId);
            return saved;
        });

        mockMvc.perform(
                        admin(post("/api/v1/admin/messages/{parentId}/restore", parentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "version": 2
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(restoredId.toString()))
                .andExpect(jsonPath("$.data.content").value("second version"))
                .andExpect(jsonPath("$.data.version").value(3));

        ArgumentCaptor<AiMessageEntity> captor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageRepository).save(captor.capture());
        assertEquals(parentId, captor.getValue().getParentMessageId());
        verify(conversationRepository).save(conversation);
    }

    private AiMessageEntity message(UUID id, UUID conversationId, int version, String content) {
        AiMessageEntity entity = new AiMessageEntity();
        entity.setId(id);
        entity.setConversationId(conversationId);
        entity.setRole("assistant");
        entity.setContent(content);
        entity.setVersion(version);
        return entity;
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
