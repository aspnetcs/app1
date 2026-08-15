package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.conversation.AiConversationEntity;
import com.webchat.platformapi.ai.conversation.AiConversationForkService;
import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.ai.conversation.AiMessageBlockService;
import com.webchat.platformapi.ai.conversation.AiMessageEntity;
import com.webchat.platformapi.ai.conversation.AiMessageRepository;
import com.webchat.platformapi.ai.conversation.ConversationController;
import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageBlockConversationBridgeTest {

    @Test
    void addMessagePersistsNormalizedBlocks() {
        AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
        AiMessageRepository messageRepository = mock(AiMessageRepository.class);
        AiMessageBlockService messageBlockService = mock(AiMessageBlockService.class);
        AiConversationForkService forkService = mock(AiConversationForkService.class);
        AuditService auditService = mock(AuditService.class);

        ConversationController controller = new ConversationController(
                conversationRepository,
                messageRepository,
                messageBlockService,
                forkService,
                auditService,
                false,
                false,
                true
        );

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle("新对话");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(AiMessageEntity.class))).thenAnswer(invocation -> {
            AiMessageEntity entity = invocation.getArgument(0);
            entity.setId(messageId);
            return entity;
        });
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Map<String, Object>> blocks = List.of(
                Map.of("type", "text", "key", "text-0", "sequence", 0, "status", "final", "payload", Map.of("text", "hello")),
                Map.of("type", "citation", "key", "citation-0", "sequence", 1, "status", "final", "payload", Map.of("title", "Doc 1"))
        );
        when(messageBlockService.buildBlocksFromMessage(eq("hello"), eq("text"), eq((String) null), any()))
                .thenReturn(blocks);

        ApiResponse<Map<String, Object>> response = controller.addMessage(
                userId,
                conversationId,
                Map.of(
                        "role", "assistant",
                        "content", "hello",
                        "content_type", "text",
                        "blocks", blocks
                )
        );

        assertEquals(0, response.code());
        assertNotNull(response.data());
        assertEquals(blocks, response.data().get("blocks"));
        verify(messageBlockService).replaceBlocks(conversationId, messageId, "assistant", blocks);
    }
}
