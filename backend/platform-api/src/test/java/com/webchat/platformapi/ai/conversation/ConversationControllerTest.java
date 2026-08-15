package com.webchat.platformapi.ai.conversation;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock
    private AiConversationRepository conversationRepository;

    @Mock
    private AiMessageRepository messageRepository;

    @Mock
    private AiConversationForkService forkService;

    @Mock
    private AuditService auditService;

    private MockMvc mockMvc;

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
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listUsesConversationsRouteAndReturnsConversationContract() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Roadmap");
        when(conversationRepository.findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId))
                .thenReturn(List.of(conversation));
        when(messageRepository.countByConversationIds(anyCollection()))
                .thenReturn(List.of(countRow(conversation.getId(), 2L)));

        mockMvc.perform(
                        get("/api/v1/conversations")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(conversation.getId().toString()))
                .andExpect(jsonPath("$.data[0].title").value("Roadmap"))
                .andExpect(jsonPath("$.data[0].mode").value("chat"))
                .andExpect(jsonPath("$.data[0].messageCount").value(2));

        verify(conversationRepository).findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId);
        verify(conversationRepository, never()).saveAll(anyCollection());
    }

    @Test
    void listPrefersPinnedTimestampOverRecentActivityForPinnedConversations() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity pinnedFirst = createConversation(userId, "Pinned A");
        pinnedFirst.setPinned(true);
        pinnedFirst.setPinnedAt(Instant.parse("2026-03-23T00:00:00Z"));
        pinnedFirst.setUpdatedAt(Instant.parse("2026-03-27T00:00:00Z"));

        AiConversationEntity pinnedSecond = createConversation(userId, "Pinned B");
        pinnedSecond.setPinned(true);
        pinnedSecond.setPinnedAt(Instant.parse("2026-03-24T00:00:00Z"));
        pinnedSecond.setUpdatedAt(Instant.parse("2026-03-28T00:00:00Z"));

        when(conversationRepository.findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId))
                .thenReturn(List.of(pinnedSecond, pinnedFirst));
        when(messageRepository.countByConversationIds(anyCollection()))
                .thenReturn(List.of(
                        countRow(pinnedFirst.getId(), 1L),
                        countRow(pinnedSecond.getId(), 1L)
                ));

        mockMvc.perform(
                        get("/api/v1/conversations")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Pinned A"))
                .andExpect(jsonPath("$.data[1].title").value("Pinned B"));
    }

    @Test
    void createUsesConversationsRouteAndReturnsSavedConversation() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity saved = createConversation(userId, "Project kickoff");
        when(conversationRepository.save(any(AiConversationEntity.class))).thenReturn(saved);

        mockMvc.perform(
                        post("/api/v1/conversations")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "Project kickoff",
                                          "model": "gpt-4o"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("Project kickoff"))
                .andExpect(jsonPath("$.data.model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.mode").value("chat"));
    }

    @Test
    void createTemporaryConversationReturnsEphemeralContractWithoutPersisting() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/conversations")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "Incognito chat",
                                          "model": "gpt-4o",
                                          "isTemporary": true
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Incognito chat"))
                .andExpect(jsonPath("$.data.model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.is_temporary").value(true))
                .andExpect(jsonPath("$.data.messageCount").value(0));

        verify(conversationRepository, never()).save(any(AiConversationEntity.class));
    }

    @Test
    void messagesUsesNestedMessagesRouteAndReturnsEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Roadmap");
        AiMessageEntity message = createMessage(conversation.getId(), "assistant", "Hello");

        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdAndParentMessageIdIsNullOrderByCreatedAtAsc(conversation.getId()))
                .thenReturn(List.of(message));

        mockMvc.perform(
                        get("/api/v1/conversations/{id}/messages", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(message.getId().toString()))
                .andExpect(jsonPath("$.data[0].content").value("Hello"));
    }

    @Test
    void addMessageUsesNestedMessagesRouteAndReturnsSavedMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Roadmap");
        AiMessageEntity message = createMessage(conversation.getId(), "user", "Hello");

        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(AiMessageEntity.class))).thenReturn(message);
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(
                        post("/api/v1/conversations/{id}/messages", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "user",
                                          "content": "Hello"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(message.getId().toString()))
                .andExpect(jsonPath("$.data.content").value("Hello"));
    }

    @Test
    void updateUsesConversationIdRouteAndReturnsUpdatedConversation() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Old title");
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.countByConversationIdAndParentMessageIdIsNull(conversation.getId())).thenReturn(0L);

        mockMvc.perform(
                        put("/api/v1/conversations/{id}", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "New title",
                                          "model": "gpt-4o-mini",
                                          "mode": "team",
                                          "captain_selection_mode": "fixed_first",
                                          "compare_models": ["model-a", "model-b"]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("New title"))
                .andExpect(jsonPath("$.data.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.mode").value("team"))
                .andExpect(jsonPath("$.data.captain_selection_mode").value("fixed_first"));
    }

    @Test
    void listReturnsTeamConversationMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Team chat");
        conversation.setMode("team");
        conversation.setCaptainSelectionMode("fixed_first");
        conversation.setCompareModelsJson("[\"model-a\",\"model-b\"]");
        when(conversationRepository.findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(userId))
                .thenReturn(List.of(conversation));
        when(messageRepository.countByConversationIds(anyCollection()))
                .thenReturn(List.of(countRow(conversation.getId(), 2L)));

        mockMvc.perform(
                        get("/api/v1/conversations")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].mode").value("team"))
                .andExpect(jsonPath("$.data[0].captain_selection_mode").value("fixed_first"))
                .andExpect(jsonPath("$.data[0].compare_models[0]").value("model-a"));
    }

    @Test
    void updateRejectsBlankTitle() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Old title");
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        mockMvc.perform(
                        put("/api/v1/conversations/{id}", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "   "
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("title is required"));

        verify(conversationRepository, never()).save(any(AiConversationEntity.class));
    }

    @Test
    void deleteUsesConversationIdRouteAndReturnsDeletedMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Delete me");
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(
                        delete("/api/v1/conversations/{id}", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("archived"));
    }

    @Test
    void archivedUsesArchivedRouteAndReturnsArchivedConversationContract() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Archived");
        conversation.setDeletedAt(Instant.parse("2026-03-25T00:00:00Z"));
        when(conversationRepository.findByUserIdAndDeletedAtIsNotNullAndTemporaryFalseOrderByDeletedAtDesc(userId))
                .thenReturn(List.of(conversation));
        when(messageRepository.countByConversationIds(anyCollection()))
                .thenReturn(List.of(countRow(conversation.getId(), 4L)));

        mockMvc.perform(
                        get("/api/v1/conversations/archived")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(conversation.getId().toString()))
                .andExpect(jsonPath("$.data[0].deleted_at").value("2026-03-25T00:00:00Z"));
    }

    @Test
    void restoreUsesRestoreRouteAndReturnsRestoredConversation() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Restore me");
        conversation.setDeletedAt(Instant.parse("2026-03-25T00:00:00Z"));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.countByConversationIdAndParentMessageIdIsNull(conversation.getId())).thenReturn(2L);

        mockMvc.perform(
                        put("/api/v1/conversations/{id}/restore", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(conversation.getId().toString()))
                .andExpect(jsonPath("$.data.deleted_at").isEmpty());
    }

    @Test
    void pinUsesPinRouteAndReturnsPinnedConversation() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Pinned");
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.countByConversationIdAndParentMessageIdIsNull(conversation.getId())).thenReturn(0L);

        mockMvc.perform(
                        put("/api/v1/conversations/{id}/pin", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"pinned\":true}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pinned").value(true));

        ArgumentCaptor<AiConversationEntity> savedConversation = ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationRepository).save(savedConversation.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(savedConversation.getValue().getPinnedAt());
    }

    @Test
    void starUsesStarRouteAndReturnsStarredConversation() throws Exception {
        UUID userId = UUID.randomUUID();
        AiConversationEntity conversation = createConversation(userId, "Starred");
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.countByConversationIdAndParentMessageIdIsNull(conversation.getId())).thenReturn(0L);

        mockMvc.perform(
                        put("/api/v1/conversations/{id}/star", conversation.getId())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"starred\":true}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.starred").value(true));
    }

    @Test
    void forkUsesNestedForkRouteAndReturnsForkedConversation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AiConversationEntity forked = createConversation(userId, "Forked");

        when(forkService.isEnabled()).thenReturn(true);
        when(forkService.forkConversation(conversationId, messageId, userId)).thenReturn(forked);
        when(messageRepository.countByConversationIdAndParentMessageIdIsNull(forked.getId())).thenReturn(3L);

        mockMvc.perform(
                        post("/api/v1/conversations/{id}/fork", conversationId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "messageId": "%s"
                                        }
                                        """.formatted(messageId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(forked.getId().toString()))
                .andExpect(jsonPath("$.data.messageCount").value(3));
    }

    @Test
    void unauthenticatedConversationRoutesAreRejected() throws Exception {
        UUID conversationId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        post("/api/v1/conversations")
                                .contentType(APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        post("/api/v1/conversations/{id}/messages", conversationId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"role\":\"user\",\"content\":\"Hello\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        put("/api/v1/conversations/{id}", conversationId)
                                .contentType(APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(delete("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/conversations/archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        put("/api/v1/conversations/{id}/pin", conversationId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"pinned\":true}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        put("/api/v1/conversations/{id}/star", conversationId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"starred\":true}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(
                        post("/api/v1/conversations/{id}/fork", conversationId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"messageId\":\"%s\"}".formatted(UUID.randomUUID()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(put("/api/v1/conversations/{id}/restore", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyConversationSingularRoutesAreNotMapped() throws Exception {
        mockMvc.perform(get("/api/v1/conversation"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/conversations/timeline"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/api/v1/conversation/timeline"))
                .andExpect(status().isNotFound());
    }

    private static AiConversationEntity createConversation(UUID userId, String title) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setModel("gpt-4o");
        entity.setCompareModelsJson("[]");
        entity.setSystemPrompt("System prompt");
        entity.setMode("chat");
        entity.setCaptainSelectionMode(null);
        entity.setTemporary(false);
        entity.setPinned(false);
        entity.setStarred(false);
        entity.setCreatedAt(Instant.parse("2026-03-23T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-23T00:05:00Z"));
        return entity;
    }

    private static AiMessageEntity createMessage(UUID conversationId, String role, String content) {
        AiMessageEntity message = new AiMessageEntity();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setContentType("text");
        message.setVersion(1);
        message.setBranchIndex(0);
        return message;
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
