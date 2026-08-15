package com.webchat.platformapi.market;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MarketCatalogService {

    private static final int MAX_SORT_ORDER = 100000;

    private final MarketCatalogItemRepository catalogRepository;
    private final UserSavedAssetRepository savedAssetRepository;

    public MarketCatalogService(
            MarketCatalogItemRepository catalogRepository,
            UserSavedAssetRepository savedAssetRepository
    ) {
        this.catalogRepository = catalogRepository;
        this.savedAssetRepository = savedAssetRepository;
    }

    public List<MarketCatalogItemEntity> listCatalogItems(boolean enabledOnly) {
        if (enabledOnly) {
            return catalogRepository.findByEnabledTrueOrderByFeaturedDescSortOrderAscCreatedAtDesc();
        }
        return catalogRepository.findAllByOrderByAssetTypeAscFeaturedDescSortOrderAscCreatedAtDesc();
    }

    public Optional<MarketCatalogItemEntity> findCatalogItem(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return catalogRepository.findById(id);
    }

    public Optional<MarketCatalogItemEntity> findCatalogItem(MarketAssetType assetType, String sourceId) {
        if (assetType == null || normalizeRequiredString(sourceId) == null) {
            return Optional.empty();
        }
        return catalogRepository.findByAssetTypeAndSourceId(assetType, normalizeRequiredString(sourceId));
    }

    @Transactional
    public MarketCatalogItemEntity createCatalogItem(Map<String, Object> body) {
        MarketCatalogItemEntity entity = new MarketCatalogItemEntity();
        applyCatalogBody(entity, body, true);
        return catalogRepository.save(entity);
    }

    @Transactional
    public MarketCatalogItemEntity updateCatalogItem(UUID id, Map<String, Object> body) {
        MarketCatalogItemEntity entity = catalogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("catalog item not found"));
        applyCatalogBody(entity, body, false);
        return catalogRepository.save(entity);
    }

    public List<UserSavedAssetEntity> listUserSavedAssets(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return savedAssetRepository.findByUserIdOrderBySortOrderAscCreatedAtDesc(userId);
    }

    public Optional<UserSavedAssetEntity> findUserSavedAsset(UUID userId, MarketAssetType assetType, String sourceId) {
        String normalizedSourceId = normalizeRequiredString(sourceId);
        if (userId == null || assetType == null || normalizedSourceId == null) {
            return Optional.empty();
        }
        return savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(userId, assetType, normalizedSourceId);
    }

    @Transactional
    public UserSavedAssetEntity saveUserAsset(
            UUID userId,
            MarketAssetType assetType,
            String sourceId,
            Map<String, Object> extraConfig
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (assetType == null) {
            throw new IllegalArgumentException("assetType is required");
        }
        String normalizedSourceId = normalizeRequiredString(sourceId);
        if (normalizedSourceId == null) {
            throw new IllegalArgumentException("sourceId is required");
        }
        UserSavedAssetEntity entity = savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(userId, assetType, normalizedSourceId)
                .orElseGet(UserSavedAssetEntity::new);
        entity.setUserId(userId);
        entity.setAssetType(assetType);
        entity.setSourceId(normalizedSourceId);
        entity.setEnabled(true);
        entity.setExtraConfigJson(copyMap(extraConfig));
        return savedAssetRepository.save(entity);
    }

    @Transactional
    public void deleteUserSavedAsset(UUID userId, MarketAssetType assetType, String sourceId) {
        String normalizedSourceId = normalizeRequiredString(sourceId);
        if (userId == null || assetType == null || normalizedSourceId == null) {
            return;
        }
        savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(userId, assetType, normalizedSourceId)
                .ifPresent(savedAssetRepository::delete);
    }

    public Map<String, Object> toCatalogPayload(MarketCatalogItemEntity entity) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId() == null ? null : entity.getId().toString());
        payload.put("assetType", entity.getAssetType() == null ? null : entity.getAssetType().name());
        payload.put("sourceId", normalizeNullableString(entity.getSourceId()));
        payload.put("title", defaultString(entity.getTitle()));
        payload.put("summary", defaultString(entity.getSummary()));
        payload.put("description", defaultString(entity.getDescription()));
        payload.put("category", defaultString(entity.getCategory()));
        payload.put("tags", defaultString(entity.getTags()));
        payload.put("tagList", splitTags(entity.getTags()));
        payload.put("cover", defaultString(entity.getCover()));
        payload.put("featured", entity.isFeatured());
        payload.put("enabled", entity.isEnabled());
        payload.put("sortOrder", entity.getSortOrder());
        payload.put("createdAt", entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
        payload.put("updatedAt", entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
        return payload;
    }

    public static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[,\\n]");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            String tag = part == null ? "" : part.trim();
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private void applyCatalogBody(MarketCatalogItemEntity entity, Map<String, Object> body, boolean creating) {
        if (body == null) {
            throw new IllegalArgumentException("request body is empty");
        }
        if (creating || body.containsKey("assetType")) {
            entity.setAssetType(MarketAssetType.fromString(String.valueOf(body.get("assetType"))));
        }
        if (creating || body.containsKey("sourceId")) {
            String sourceId = normalizeRequiredString(body.get("sourceId"));
            if (sourceId == null) {
                throw new IllegalArgumentException("sourceId is required");
            }
            entity.setSourceId(sourceId);
        }
        if (body.containsKey("title")) {
            entity.setTitle(defaultString(body.get("title")));
        } else if (creating && entity.getTitle() == null) {
            entity.setTitle("");
        }
        if (body.containsKey("summary")) {
            entity.setSummary(normalizeNullableString(body.get("summary")));
        }
        if (body.containsKey("description")) {
            entity.setDescription(normalizeNullableString(body.get("description")));
        }
        if (body.containsKey("category")) {
            entity.setCategory(normalizeNullableString(body.get("category")));
        }
        if (body.containsKey("tags")) {
            entity.setTags(defaultString(body.get("tags")));
        }
        if (body.containsKey("cover")) {
            entity.setCover(defaultString(body.get("cover")));
        }
        if (body.containsKey("featured")) {
            entity.setFeatured(toBoolean(body.get("featured"), entity.isFeatured()));
        }
        if (body.containsKey("enabled")) {
            entity.setEnabled(toBoolean(body.get("enabled"), entity.isEnabled()));
        }
        if (body.containsKey("sortOrder")) {
            entity.setSortOrder(parseInt(body.get("sortOrder"), entity.getSortOrder()));
        }
    }

    private static boolean toBoolean(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Math.max(0, Math.min(MAX_SORT_ORDER, Integer.parseInt(String.valueOf(raw).trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normalizeRequiredString(Object raw) {
        String value = normalizeNullableString(raw);
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeNullableString(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static String defaultString(Object raw) {
        String value = normalizeNullableString(raw);
        return value == null ? "" : value;
    }

    private static Map<String, Object> copyMap(Map<String, Object> raw) {
        return raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
    }
}
