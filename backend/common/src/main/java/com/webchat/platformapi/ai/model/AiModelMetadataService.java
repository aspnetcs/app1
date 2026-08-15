package com.webchat.platformapi.ai.model;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiModelMetadataService {

    private final AiModelMetadataRepository repository;
    private final ChannelRouter channelRouter;
    private final AiChannelRepository channelRepo;
    private final AiModelCapabilityResolver capabilityResolver;

    public AiModelMetadataService(AiModelMetadataRepository repository, ChannelRouter channelRouter, AiChannelRepository channelRepo, AiModelCapabilityResolver capabilityResolver) {
        this.repository = repository;
        this.channelRouter = channelRouter;
        this.channelRepo = channelRepo;
        this.capabilityResolver = capabilityResolver;
    }

    public Map<String, AiModelMetadataEntity> findByModelIds(Collection<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Map.of();
        }
        List<AiModelMetadataEntity> entries = repository.findAllByModelIdIn(modelIds);
        Map<String, AiModelMetadataEntity> map = new HashMap<>();
        for (AiModelMetadataEntity entry : entries) {
            if (entry != null && entry.getModelId() != null) {
                map.put(entry.getModelId(), entry);
            }
        }
        return map;
    }

    public Page<Map<String, Object>> listCatalog(Pageable pageable) {
        return listCatalog(pageable, null);
    }

    public Page<Map<String, Object>> listCatalog(Pageable pageable, String keyword) {
        List<Map<String, Object>> items = buildCatalogItems(keyword);

        int total = items.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(items.subList(from, to), pageable, total);
    }

    public List<Map<String, Object>> listCatalogAll(String keyword) {
        return buildCatalogItems(keyword);
    }

    private static boolean matchesKeyword(Map<String, Object> item, String keyword) {
        if (keyword == null) {
            return true;
        }
        String loweredKeyword = keyword.toLowerCase(Locale.ROOT);
        return containsKeyword(item.get("modelId"), loweredKeyword)
                || containsKeyword(item.get("name"), loweredKeyword)
                || containsKeyword(item.get("description"), loweredKeyword);
    }

    private static boolean containsKeyword(Object value, String keyword) {
        if (value == null) {
            return false;
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private List<Map<String, Object>> buildCatalogItems(String keyword) {
        Set<String> discovered = discoverModelIds();
        Map<String, AiModelMetadataEntity> metadata = findByModelIds(discovered);
        List<Map<String, Object>> items = new ArrayList<>();
        String normalizedKeyword = RequestUtils.trimOrNull(keyword);
        for (String modelId : discovered) {
            AiModelMetadataEntity entity = metadata.get(modelId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelId", modelId);
            item.put("name", entity != null && entity.getName() != null ? entity.getName() : modelId);
            item.put("avatar", entity != null ? entity.getAvatar() : null);
            item.put("description", entity != null ? entity.getDescription() : null);
            item.put("pinned", entity != null && entity.isPinned());
            item.put("sortOrder", entity != null ? entity.getSortOrder() : 0);
            item.put("defaultSelected", entity != null && entity.isDefaultSelected());
            item.put("multiChatEnabled", entity == null || entity.isMultiChatEnabled());
            item.put("billingEnabled", entity != null && entity.isBillingEnabled());
            item.put("requestPriceUsd", entity != null ? entity.getRequestPriceUsd() : null);
            item.put("promptPriceUsd", entity != null ? entity.getPromptPriceUsd() : null);
            item.put("inputPriceUsdPer1M", entity != null ? entity.getInputPriceUsdPer1M() : null);
            item.put("outputPriceUsdPer1M", entity != null ? entity.getOutputPriceUsdPer1M() : null);
            Boolean imageOverride = entity != null ? entity.getImageParsingOverride() : null;
            AiModelCapabilityResolver.ImageCapability cap = capabilityResolver.resolve(modelId, imageOverride);
            item.put("supportsImageParsing", cap.supportsImageParsing());
            item.put("supportsImageParsingSource", cap.source());
            item.put("imageParsingOverride", imageOverride);
            if (matchesKeyword(item, normalizedKeyword)) {
                items.add(item);
            }
        }

        items.sort((a, b) -> {
            int pinnedCmp = Boolean.compare(Boolean.TRUE.equals((Boolean) b.get("pinned")), Boolean.TRUE.equals((Boolean) a.get("pinned")));
            if (pinnedCmp != 0) return pinnedCmp;
            int sortCmp = Integer.compare((Integer) a.get("sortOrder"), (Integer) b.get("sortOrder"));
            if (sortCmp != 0) return sortCmp;
            return String.valueOf(a.get("modelId")).compareToIgnoreCase(String.valueOf(b.get("modelId")));
        });
        return items;
    }

    @Transactional
    public AiModelMetadataEntity upsert(String modelId, ModelMetadataRequest request) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request body required");
        }
        if (!discoverModelIds().contains(modelId)) {
            throw new IllegalArgumentException("modelId not discovered from channels");
        }
        validateLength("name", request.getName(), 150);
        validateLength("avatar", request.getAvatar(), 150);
        validateNonNegative("requestPriceUsd", request.getRequestPriceUsd());
        validateNonNegative("promptPriceUsd", request.getPromptPriceUsd());
        validateNonNegative("inputPriceUsdPer1M", request.getInputPriceUsdPer1M());
        validateNonNegative("outputPriceUsdPer1M", request.getOutputPriceUsdPer1M());
        if (request.getSortOrder() != null && request.getSortOrder() < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        AiModelMetadataEntity entity = repository.findByModelId(modelId).orElseGet(AiModelMetadataEntity::new);
        if (entity.getId() == null) {
            entity.setPinned(false);
            entity.setSortOrder(0);
        }
        entity.setModelId(modelId);
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getAvatar() != null) entity.setAvatar(request.getAvatar());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getPinned() != null) entity.setPinned(request.getPinned());
        if (request.getSortOrder() != null) entity.setSortOrder(request.getSortOrder());
        if (request.getDefaultSelected() != null) {
            if (request.getDefaultSelected()) {
                repository.findByDefaultSelectedTrue().ifPresent(existing -> {
                    if (!existing.getModelId().equals(modelId)) {
                        existing.setDefaultSelected(false);
                        repository.save(existing);
                    }
                });
            }
            entity.setDefaultSelected(request.getDefaultSelected());
        }
        if (request.getMultiChatEnabled() != null) {
            entity.setMultiChatEnabled(request.getMultiChatEnabled());
        }
        if (request.getBillingEnabled() != null) {
            entity.setBillingEnabled(request.getBillingEnabled());
            if (!request.getBillingEnabled()) {
                entity.setRequestPriceUsd(null);
                entity.setPromptPriceUsd(null);
                entity.setInputPriceUsdPer1M(null);
                entity.setOutputPriceUsdPer1M(null);
            }
        }
        if (request.getRequestPriceUsd() != null) {
            entity.setRequestPriceUsd(request.getRequestPriceUsd());
        }
        if (request.getPromptPriceUsd() != null) {
            entity.setPromptPriceUsd(request.getPromptPriceUsd());
        }
        if (request.getInputPriceUsdPer1M() != null) {
            entity.setInputPriceUsdPer1M(request.getInputPriceUsdPer1M());
        }
        if (request.getOutputPriceUsdPer1M() != null) {
            entity.setOutputPriceUsdPer1M(request.getOutputPriceUsdPer1M());
        }
        if (request.hasImageParsingOverride()) {
            entity.setImageParsingOverride(request.getImageParsingOverride());
        }
        return repository.save(entity);
    }

    @Transactional
    public void deleteByModelId(String modelId) {
        repository.deleteByModelId(modelId);
        // Also remove from all channel models fields so it disappears from discovery
        if (modelId == null || modelId.isBlank()) return;
        for (AiChannelEntity channel : channelRepo.findAll()) {
            if (channel == null || channel.getModels() == null || channel.getModels().isBlank()) continue;
            String[] parts = channel.getModels().split(",");
            List<String> kept = new ArrayList<>();
            boolean changed = false;
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.equals(modelId)) {
                    changed = true;
                } else if (!trimmed.isEmpty()) {
                    kept.add(trimmed);
                }
            }
            if (changed) {
                channel.setModels(kept.isEmpty() ? null : String.join(",", kept));
                channelRepo.save(channel);
            }
        }
    }

    @Transactional
    public void reorder(List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            throw new IllegalArgumentException("modelIds required");
        }
        Map<String, AiModelMetadataEntity> existing = findByModelIds(modelIds);
        Map<String, AiModelMetadataEntity> seen = new LinkedHashMap<>();
        int index = 0;
        for (String modelId : modelIds) {
            if (modelId == null || modelId.isBlank()) continue;
            if (seen.containsKey(modelId)) continue;
            AiModelMetadataEntity entity = existing.getOrDefault(modelId, new AiModelMetadataEntity());
            entity.setModelId(modelId);
            entity.setSortOrder(index++);
            seen.put(modelId, entity);
        }
        if (!seen.isEmpty()) {
            repository.saveAll(seen.values());
        }
    }

    private static void validateLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " too long");
        }
    }

    private static void validateNonNegative(String field, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
    }

    private Set<String> discoverModelIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (AiChannelEntity channel : channelRouter.listRoutableChannels()) {
            if (channel == null) continue;
            if (channel.getModels() != null && !channel.getModels().isBlank()) {
                for (String model : channel.getModels().split(",")) {
                    String trimmed = model.trim();
                    if (!trimmed.isEmpty()) ids.add(trimmed);
                }
            }
            if (channel.getModelMapping() != null) ids.addAll(channel.getModelMapping().keySet());
        }
        return ids;
    }
}
