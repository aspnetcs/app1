package com.webchat.platformapi.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSavedAssetRepository extends JpaRepository<UserSavedAssetEntity, UUID> {

    List<UserSavedAssetEntity> findByUserIdOrderBySortOrderAscCreatedAtDesc(UUID userId);

    List<UserSavedAssetEntity> findByUserIdAndAssetTypeOrderBySortOrderAscCreatedAtDesc(UUID userId, MarketAssetType assetType);

    Optional<UserSavedAssetEntity> findByUserIdAndAssetTypeAndSourceId(UUID userId, MarketAssetType assetType, String sourceId);
}
