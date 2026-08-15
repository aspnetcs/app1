package com.webchat.platformapi.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketCatalogItemRepository extends JpaRepository<MarketCatalogItemEntity, UUID> {

    List<MarketCatalogItemEntity> findAllByOrderByAssetTypeAscFeaturedDescSortOrderAscCreatedAtDesc();

    List<MarketCatalogItemEntity> findByEnabledTrueOrderByFeaturedDescSortOrderAscCreatedAtDesc();

    Optional<MarketCatalogItemEntity> findByAssetTypeAndSourceId(MarketAssetType assetType, String sourceId);
}
