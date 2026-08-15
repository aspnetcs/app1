package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.knowledge.DeterministicKnowledgeEmbeddingService;
import com.webchat.platformapi.knowledge.KnowledgeBaseEntity;
import com.webchat.platformapi.knowledge.KnowledgeBaseRepository;
import com.webchat.platformapi.knowledge.KnowledgeChunkRepository;
import com.webchat.platformapi.knowledge.KnowledgeChunkSearchRow;
import com.webchat.platformapi.knowledge.KnowledgeDocumentEntity;
import com.webchat.platformapi.knowledge.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatKnowledgeContextServiceTest {

    @Test
    void applyKnowledgeContextInjectsRetrievedSnippetsAndRemovesRequestField() {
        KnowledgeBaseRepository baseRepository = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
        ChatKnowledgeContextService service = new ChatKnowledgeContextService(
                baseRepository,
                documentRepository,
                chunkRepository,
                new DeterministicKnowledgeEmbeddingService(),
                new ObjectMapper(),
                true,
                4,
                4,
                1200
        );

        UUID userId = UUID.randomUUID();
        UUID baseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        KnowledgeBaseEntity base = new KnowledgeBaseEntity();
        base.setId(baseId);
        base.setOwnerUserId(userId);
        base.setName("Product Docs");
        base.setDescription("Internal docs");
        base.setRetrievalLimit(4);
        base.setSimilarityThreshold(0.1d);

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(documentId);
        document.setBaseId(baseId);
        document.setTitle("Getting Started");
        document.setSourceUri("https://example.com/docs/getting-started");

        when(baseRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(baseId, userId)).thenReturn(Optional.of(base));
        when(documentRepository.findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(baseId)).thenReturn(List.of(document));
        when(chunkRepository.searchByBaseId(org.mockito.ArgumentMatchers.eq(baseId), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(4)))
                .thenReturn(List.of(searchRow(documentId, "Install the dependency and restart the service.", 0.92d)));

        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("knowledgeBaseIds", List.of(baseId.toString()));
        request.put("messages", List.of(Map.of("role", "user", "content", "Please answer based on the selected knowledge base: how do I install it?")));

        service.applyKnowledgeContext(userId, request);

        assertThat(request).doesNotContainKey("knowledgeBaseIds");
        String prompt = String.valueOf(request.get(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY));
        assertThat(prompt).contains("usageMode=retrieval");
        assertThat(prompt).contains("The retrieved snippets below are the knowledge-base content selected for this request.");
        assertThat(prompt).contains("do not say that the knowledge base content was not provided");
        assertThat(prompt).contains("Product Docs");
        assertThat(prompt).contains("Getting Started");
        assertThat(prompt).contains("Install the dependency");
        assertThat(prompt).contains("RetrievalQuery: how do I install it?");
        List<?> messages = (List<?>) request.get("messages");
        Map<?, ?> latestUserMessage = (Map<?, ?>) messages.get(messages.size() - 1);
        assertThat(String.valueOf(latestUserMessage.get("content")))
                .contains("Use the retrieved knowledge snippets below as the factual basis for your answer.")
                .contains("Original user request:")
                .contains("Please answer based on the selected knowledge base: how do I install it?");
        assertThat(String.valueOf(request.get(ChatKnowledgeContextService.DIRECT_ANSWER_KEY)))
                .contains("Install the dependency and restart the service.");
    }

    private static KnowledgeChunkSearchRow searchRow(UUID documentId, String chunkText, double similarity) {
        return new KnowledgeChunkSearchRow() {
            @Override
            public UUID getId() {
                return UUID.randomUUID();
            }

            @Override
            public UUID getBaseId() {
                return UUID.randomUUID();
            }

            @Override
            public UUID getDocumentId() {
                return documentId;
            }

            @Override
            public Integer getChunkNo() {
                return 0;
            }

            @Override
            public String getChunkText() {
                return chunkText;
            }

            @Override
            public String getMetadataJson() {
                return "{}";
            }

            @Override
            public Double getSimilarity() {
                return similarity;
            }
        };
    }
}
