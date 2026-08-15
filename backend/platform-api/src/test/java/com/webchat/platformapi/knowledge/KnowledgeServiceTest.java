package com.webchat.platformapi.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.config.SysConfigService;
import com.webchat.platformapi.market.ContextAssetContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock private KnowledgeBaseRepository baseRepository;
    @Mock private KnowledgeDocumentRepository documentRepository;
    @Mock private KnowledgeChunkRepository chunkRepository;
    @Mock private KnowledgeIngestJobRepository ingestJobRepository;
    @Mock private KnowledgeConversationBindingRepository bindingRepository;
    @Mock private SysConfigService sysConfigService;

    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new KnowledgeService(
                baseRepository,
                documentRepository,
                chunkRepository,
                ingestJobRepository,
                bindingRepository,
                new DeterministicKnowledgeEmbeddingService(),
                new ObjectMapper(),
                sysConfigService,
                true,
                12,
                200
        );
        when(sysConfigService.get(any())).thenReturn(Optional.empty());
    }

    @Test
    void ingestDocumentCreatesChunksAndCompletesJob() {
        UUID userId = UUID.randomUUID();
        UUID baseId = UUID.randomUUID();

        KnowledgeBaseEntity base = new KnowledgeBaseEntity();
        base.setId(baseId);
        base.setOwnerUserId(userId);
        base.setName("Docs");
        base.setChunkSize(240);
        base.setChunkOverlap(20);
        base.setRetrievalLimit(5);
        base.setSimilarityThreshold(0.1d);

        when(baseRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(baseId, userId)).thenReturn(Optional.of(base));
        when(documentRepository.findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(baseId)).thenReturn(List.of());
        when(documentRepository.save(any(KnowledgeDocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ingestJobRepository.save(any(KnowledgeIngestJobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = knowledgeService.ingestDocument(userId, baseId, Map.of(
                "title", "Guide",
                "sourceType", "note",
                "content", "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen"
        ));

        assertEquals("Guide", ((Map<?, ?>) result.get("document")).get("title"));
        assertEquals("completed", ((Map<?, ?>) result.get("job")).get("status"));
        assertEquals(ContextAssetContract.USAGE_MODE_RETRIEVAL, ((Map<?, ?>) result.get("base")).get("usageMode"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeChunkEntity>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunkCaptor.capture());
        assertFalse(chunkCaptor.getValue().isEmpty());
    }

    @Test
    void createBaseRejectsSuspiciousPlaceholderNames() {
        UUID userId = UUID.randomUUID();
        when(baseRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId)).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                knowledgeService.createBase(userId, Map.of(
                        "name", "???????",
                        "description", "?????????? FAQ"
                )));

        assertEquals("knowledge base name contains invalid placeholder characters", error.getMessage());
    }

    @Test
    void createBaseDropsSuspiciousPlaceholderDescriptions() {
        UUID userId = UUID.randomUUID();
        when(baseRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId)).thenReturn(List.of());
        when(baseRepository.save(any(KnowledgeBaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(any())).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = knowledgeService.createBase(userId, Map.of(
                "name", "Account Recovery FAQ",
                "description", "?????????? FAQ"
        ));

        ArgumentCaptor<KnowledgeBaseEntity> baseCaptor = ArgumentCaptor.forClass(KnowledgeBaseEntity.class);
        verify(baseRepository).save(baseCaptor.capture());
        assertNull(baseCaptor.getValue().getDescription());
        assertEquals("", result.get("description"));
    }
}
