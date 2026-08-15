package com.webchat.platformapi.market;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketCatalogServiceTest {

    @Mock
    private MarketCatalogItemRepository catalogRepository;

    @Mock
    private UserSavedAssetRepository savedAssetRepository;

    private MarketCatalogService service;

    @BeforeEach
    void setUp() {
        service = new MarketCatalogService(catalogRepository, savedAssetRepository);
    }

    @Test
    void createCatalogItemNormalizesPayload() {
        when(catalogRepository.save(any(MarketCatalogItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarketCatalogItemEntity entity = service.createCatalogItem(Map.of(
                "assetType", "agent",
                "sourceId", "  source-1  ",
                "title", "  Agent Card  ",
                "tags", "alpha, beta, alpha",
                "sortOrder", "9",
                "featured", true
        ));

        assertEquals(MarketAssetType.AGENT, entity.getAssetType());
        assertEquals("source-1", entity.getSourceId());
        assertEquals("Agent Card", entity.getTitle());
        assertEquals("alpha, beta, alpha", entity.getTags());
        assertEquals(9, entity.getSortOrder());
        assertTrue(entity.isFeatured());
        assertEquals(2, MarketCatalogService.splitTags(entity.getTags()).size());
    }

    @Test
    void saveUserAssetUpsertsExistingRelation() {
        UUID userId = UUID.randomUUID();
        UserSavedAssetEntity existing = new UserSavedAssetEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setAssetType(MarketAssetType.MCP);
        existing.setSourceId("12");

        when(savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(userId, MarketAssetType.MCP, "12"))
                .thenReturn(Optional.of(existing));
        when(savedAssetRepository.save(any(UserSavedAssetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveUserAsset(userId, MarketAssetType.MCP, "12", Map.of("mode", "auto"));

        ArgumentCaptor<UserSavedAssetEntity> captor = ArgumentCaptor.forClass(UserSavedAssetEntity.class);
        verify(savedAssetRepository).save(captor.capture());
        assertEquals(existing.getId(), captor.getValue().getId());
        assertEquals("auto", captor.getValue().getExtraConfigJson().get("mode"));
        assertTrue(captor.getValue().isEnabled());
    }
}
