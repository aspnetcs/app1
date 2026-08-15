package com.webchat.platformapi.market;

import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.agent.AgentScope;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.ai.extension.McpServerEntity;
import com.webchat.platformapi.ai.extension.McpServerRepository;
import com.webchat.platformapi.config.SysConfigService;
import com.webchat.platformapi.knowledge.KnowledgeBaseEntity;
import com.webchat.platformapi.knowledge.KnowledgeBaseRepository;
import com.webchat.platformapi.knowledge.KnowledgeDocumentRepository;
import com.webchat.platformapi.skill.LocalSkillDefinition;
import com.webchat.platformapi.skill.LocalSkillDiscoveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlatformMarketService {

    static final String KEY_MARKET_ENABLED = "platform.market.unified.enabled";
    static final String KEY_MCP_USER_CENTER_ENABLED = "platform.mcp.user-center.enabled";

    private final MarketCatalogItemRepository catalogRepository;
    private final UserSavedAssetRepository savedAssetRepository;
    private final AgentRepository agentRepository;
    private final AgentService agentService;
    private final McpServerRepository mcpServerRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final LocalSkillDiscoveryService localSkillDiscoveryService;
    private final SysConfigService sysConfigService;

    public PlatformMarketService(
            MarketCatalogItemRepository catalogRepository,
            UserSavedAssetRepository savedAssetRepository,
            AgentRepository agentRepository,
            AgentService agentService,
            McpServerRepository mcpServerRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            LocalSkillDiscoveryService localSkillDiscoveryService,
            SysConfigService sysConfigService
    ) {
        this.catalogRepository = catalogRepository;
        this.savedAssetRepository = savedAssetRepository;
        this.agentRepository = agentRepository;
        this.agentService = agentService;
        this.mcpServerRepository = mcpServerRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.localSkillDiscoveryService = localSkillDiscoveryService;
        this.sysConfigService = sysConfigService;
    }

    public List<Map<String, Object>> listAssets(UUID userId, String assetType, String search) {
        if (!isMarketEnabled()) {
            return List.of();
        }
        MarketAssetType filterType = parseOptionalAssetType(assetType);
        Map<String, UserSavedAssetEntity> savedLookup = new LinkedHashMap<>();
        for (UserSavedAssetEntity entity : savedAssetRepository.findByUserIdOrderBySortOrderAscCreatedAtDesc(userId)) {
            savedLookup.put(savedKey(entity.getAssetType(), entity.getSourceId()), entity);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (MarketCatalogItemEntity catalogItem : catalogRepository.findByEnabledTrueOrderByFeaturedDescSortOrderAscCreatedAtDesc()) {
            if (filterType != null && catalogItem.getAssetType() != filterType) {
                continue;
            }
            UserSavedAssetEntity saved = savedLookup.get(savedKey(catalogItem.getAssetType(), catalogItem.getSourceId()));
            resolveCatalogAsset(userId, catalogItem, saved)
                    .filter(item -> matchesSearch(item, search))
                    .ifPresent(items::add);
        }
        return items;
    }

    public List<Map<String, Object>> listSavedAssets(UUID userId, String assetType) {
        MarketAssetType filterType = parseOptionalAssetType(assetType);
        List<Map<String, Object>> items = new ArrayList<>();
        for (UserSavedAssetEntity entity : savedAssetRepository.findByUserIdOrderBySortOrderAscCreatedAtDesc(userId)) {
            if (filterType != null && entity.getAssetType() != filterType) {
                continue;
            }
            resolveSavedAsset(userId, entity).ifPresent(items::add);
        }
        return items;
    }

    @Transactional
    public Map<String, Object> saveAsset(UUID userId, MarketAssetType assetType, String sourceId) {
        requireMarketEnabled();
        String normalizedSourceId = requireSourceId(sourceId);
        UserSavedAssetEntity entity = savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(userId, assetType, normalizedSourceId)
                .orElseGet(() -> createSavedAsset(userId, assetType, normalizedSourceId));
        Map<String, Object> extraConfig = new LinkedHashMap<>(entity.getExtraConfigJson());

        switch (assetType) {
            case AGENT -> saveAgentAsset(userId, normalizedSourceId, extraConfig);
            case KNOWLEDGE -> requireOwnedKnowledgeBase(userId, normalizedSourceId);
            case MCP -> requireEnabledMcpServer(normalizedSourceId);
            case SKILL -> requireAvailableSkill(normalizedSourceId);
        }

        entity.setEnabled(true);
        entity.setExtraConfigJson(extraConfig);
        UserSavedAssetEntity saved = savedAssetRepository.save(entity);
        return resolveSavedAsset(userId, saved).orElseGet(() -> basicSavedPayload(saved, null));
    }

    @Transactional
    public void unsaveAsset(UUID userId, MarketAssetType assetType, String sourceId) {
        savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(userId, assetType, requireSourceId(sourceId))
                .ifPresent(savedAssetRepository::delete);
    }

    private Optional<Map<String, Object>> resolveCatalogAsset(
            UUID userId,
            MarketCatalogItemEntity catalogItem,
            UserSavedAssetEntity savedAsset
    ) {
        return switch (catalogItem.getAssetType()) {
            case AGENT -> resolveAgentPayload(catalogItem, savedAsset);
            case MCP -> resolveMcpPayload(catalogItem, savedAsset);
            case KNOWLEDGE -> resolveKnowledgePayload(userId, catalogItem, savedAsset);
            case SKILL -> resolveSkillPayload(catalogItem, savedAsset);
        };
    }

    private Optional<Map<String, Object>> resolveSavedAsset(UUID userId, UserSavedAssetEntity savedAsset) {
        MarketCatalogItemEntity catalogItem = catalogRepository.findByAssetTypeAndSourceId(savedAsset.getAssetType(), savedAsset.getSourceId()).orElse(null);
        return switch (savedAsset.getAssetType()) {
            case AGENT -> resolveAgentPayload(catalogItem, savedAsset);
            case MCP -> resolveMcpPayload(catalogItem, savedAsset);
            case KNOWLEDGE -> resolveKnowledgePayload(userId, catalogItem, savedAsset);
            case SKILL -> resolveSkillPayload(catalogItem, savedAsset);
        };
    }

    private Optional<Map<String, Object>> resolveAgentPayload(MarketCatalogItemEntity catalogItem, UserSavedAssetEntity savedAsset) {
        String sourceId = savedAsset != null ? savedAsset.getSourceId() : catalogItem.getSourceId();
        UUID agentId = parseUuid(requireSourceId(sourceId), "agent sourceId is invalid");
        Optional<AgentEntity> agentOptional = agentRepository.findByIdAndDeletedAtIsNull(agentId);
        if (agentOptional.isEmpty()) {
            return Optional.empty();
        }
        AgentEntity agent = agentOptional.get();
        if (agent.getScope() != AgentScope.SYSTEM || !agent.isEnabled()) {
            return Optional.empty();
        }

        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("id", agent.getId().toString());
        source.put("name", agent.getName());
        source.put("description", defaultString(agent.getDescription()));
        source.put("category", defaultString(agent.getCategory()));
        source.put("tags", toTagList(agent.getTags()));
        source.put("author", defaultString(agent.getAuthor()));
        source.put("featured", agent.isFeatured());
        source.put("installCount", agent.getInstallCount());

        return Optional.of(buildAssetPayload(MarketAssetType.AGENT, catalogItem, savedAsset, source, "install"));
    }

    private Optional<Map<String, Object>> resolveMcpPayload(MarketCatalogItemEntity catalogItem, UserSavedAssetEntity savedAsset) {
        String sourceId = savedAsset != null ? savedAsset.getSourceId() : catalogItem.getSourceId();
        Long serverId = parseLong(requireSourceId(sourceId), "mcp sourceId is invalid");
        Optional<McpServerEntity> serverOptional = mcpServerRepository.findById(serverId);
        if (serverOptional.isEmpty()) {
            return Optional.empty();
        }
        McpServerEntity server = serverOptional.get();
        if (!server.isEnabled()) {
            return Optional.empty();
        }

        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("id", String.valueOf(server.getId()));
        source.put("name", server.getName());
        source.put("description", defaultString(server.getDescription()));
        source.put("transportType", defaultString(server.getTransportType()));
        source.put("lastRefreshedAt", server.getLastRefreshedAt() == null ? "" : server.getLastRefreshedAt().toString());

        return Optional.of(buildAssetPayload(MarketAssetType.MCP, catalogItem, savedAsset, source, "reference"));
    }

    private Optional<Map<String, Object>> resolveKnowledgePayload(
            UUID userId,
            MarketCatalogItemEntity catalogItem,
            UserSavedAssetEntity savedAsset
    ) {
        String sourceId = savedAsset != null ? savedAsset.getSourceId() : catalogItem.getSourceId();
        UUID baseId = parseUuid(sourceId, "knowledge sourceId is invalid");
        Optional<KnowledgeBaseEntity> baseOptional = savedAsset != null
                ? knowledgeBaseRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(baseId, userId)
                : knowledgeBaseRepository.findById(baseId).filter(base -> base.getDeletedAt() == null);
        if (baseOptional.isEmpty()) {
            return Optional.empty();
        }
        KnowledgeBaseEntity base = baseOptional.get();

        LinkedHashMap<String, Object> source = new LinkedHashMap<>(ContextAssetContract.knowledgeContractPayload());
        source.put("id", base.getId().toString());
        source.put("name", base.getName());
        source.put("description", defaultString(base.getDescription()));
        source.put("documentCount", knowledgeDocumentRepository.findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(base.getId()).size());

        Map<String, Object> payload = buildAssetPayload(MarketAssetType.KNOWLEDGE, catalogItem, savedAsset, source, "reference");
        payload.putAll(ContextAssetContract.knowledgeContractPayload());
        return Optional.of(payload);
    }

    private Optional<Map<String, Object>> resolveSkillPayload(
            MarketCatalogItemEntity catalogItem,
            UserSavedAssetEntity savedAsset
    ) {
        String sourceId = savedAsset != null ? savedAsset.getSourceId() : catalogItem.getSourceId();
        Optional<LocalSkillDefinition> skillOptional = localSkillDiscoveryService.getSkill(sourceId);
        if (skillOptional.isEmpty()) {
            if (savedAsset == null) {
                return Optional.empty();
            }
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>(basicSavedPayload(savedAsset, catalogItem));
            payload.putAll(ContextAssetContract.skillContractPayload());
            payload.put("available", false);
            payload.put("source", Map.of(
                    "id", savedAsset.getSourceId(),
                    "entryFile", "SKILL.md",
                    "available", false,
                    "contentFormat", ContextAssetContract.CONTENT_FORMAT,
                    "usageMode", ContextAssetContract.USAGE_MODE_FULL_INSTRUCTION,
                    "aiUsageInstruction", ContextAssetContract.skillUsageInstruction()
            ));
            return Optional.of(payload);
        }

        LocalSkillDefinition skill = skillOptional.get();
        LinkedHashMap<String, Object> source = new LinkedHashMap<>(ContextAssetContract.skillContractPayload());
        source.put("id", skill.sourceId());
        source.put("name", skill.name());
        source.put("description", skill.description());
        source.put("entryFile", skill.entryFile());
        source.put("absolutePath", skill.absolutePath());
        source.put("relativePath", skill.relativePath());
        source.put("content", skill.content());
        source.put("contentPreview", skill.contentPreview());
        source.put("contentBytes", skill.contentBytes());
        source.put("metadata", skill.metadata());

        Map<String, Object> payload = buildAssetPayload(MarketAssetType.SKILL, catalogItem, savedAsset, source, "reference");
        payload.putAll(ContextAssetContract.skillContractPayload());
        return Optional.of(payload);
    }

    private Map<String, Object> buildAssetPayload(
            MarketAssetType assetType,
            MarketCatalogItemEntity catalogItem,
            UserSavedAssetEntity savedAsset,
            Map<String, Object> source,
            String saveMode
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        String sourceId = savedAsset != null ? savedAsset.getSourceId() : catalogItem.getSourceId();
        payload.put("assetType", assetType.name());
        payload.put("sourceId", sourceId);
        payload.put("catalogId", catalogItem == null || catalogItem.getId() == null ? null : catalogItem.getId().toString());
        payload.put("title", firstNonBlank(catalogItem == null ? null : catalogItem.getTitle(), source.get("name")));
        payload.put("summary", firstNonBlank(catalogItem == null ? null : catalogItem.getSummary(), source.get("description")));
        payload.put("description", firstNonBlank(catalogItem == null ? null : catalogItem.getDescription(), source.get("description")));
        payload.put("category", firstNonBlank(catalogItem == null ? null : catalogItem.getCategory(), source.get("category")));
        payload.put("tags", catalogItem == null ? toTagList(source.get("tags")) : toTagList(catalogItem.getTags()));
        payload.put("cover", catalogItem == null ? "" : defaultString(catalogItem.getCover()));
        payload.put("featured", catalogItem != null && catalogItem.isFeatured());
        payload.put("sortOrder", catalogItem == null ? (savedAsset == null ? 0 : savedAsset.getSortOrder()) : catalogItem.getSortOrder());
        payload.put("saved", savedAsset != null);
        payload.put("saveMode", saveMode);
        payload.put("enabled", savedAsset == null || savedAsset.isEnabled());
        payload.put("available", true);
        payload.put("extraConfig", savedAsset == null ? Map.of() : new LinkedHashMap<>(savedAsset.getExtraConfigJson()));
        payload.put("source", source);
        if (savedAsset != null) {
            payload.put("savedAt", savedAsset.getCreatedAt() == null ? "" : savedAsset.getCreatedAt().toString());
            payload.put("updatedAt", savedAsset.getUpdatedAt() == null ? "" : savedAsset.getUpdatedAt().toString());
        }
        return payload;
    }

    private Map<String, Object> basicSavedPayload(UserSavedAssetEntity savedAsset, MarketCatalogItemEntity catalogItem) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetType", savedAsset.getAssetType().name());
        payload.put("sourceId", savedAsset.getSourceId());
        payload.put("catalogId", catalogItem == null || catalogItem.getId() == null ? null : catalogItem.getId().toString());
        payload.put("title", catalogItem == null ? savedAsset.getSourceId() : firstNonBlank(catalogItem.getTitle(), savedAsset.getSourceId()));
        payload.put("summary", catalogItem == null ? "" : defaultString(catalogItem.getSummary()));
        payload.put("description", catalogItem == null ? "" : defaultString(catalogItem.getDescription()));
        payload.put("category", catalogItem == null ? "" : defaultString(catalogItem.getCategory()));
        payload.put("tags", catalogItem == null ? List.of() : toTagList(catalogItem.getTags()));
        payload.put("cover", catalogItem == null ? "" : defaultString(catalogItem.getCover()));
        payload.put("featured", catalogItem != null && catalogItem.isFeatured());
        payload.put("sortOrder", catalogItem == null ? savedAsset.getSortOrder() : catalogItem.getSortOrder());
        payload.put("saved", true);
        payload.put("saveMode", savedAsset.getAssetType() == MarketAssetType.AGENT ? "install" : "reference");
        payload.put("enabled", savedAsset.isEnabled());
        payload.put("available", true);
        payload.put("extraConfig", new LinkedHashMap<>(savedAsset.getExtraConfigJson()));
        payload.put("source", Map.of("id", savedAsset.getSourceId()));
        payload.put("savedAt", savedAsset.getCreatedAt() == null ? "" : savedAsset.getCreatedAt().toString());
        payload.put("updatedAt", savedAsset.getUpdatedAt() == null ? "" : savedAsset.getUpdatedAt().toString());
        return payload;
    }

    private void saveAgentAsset(UUID userId, String sourceId, Map<String, Object> extraConfig) {
        if (!extraConfig.containsKey("installedAgentId")) {
            Map<String, Object> installed = agentService.installAgent(userId, parseUuid(sourceId, "agent sourceId is invalid"));
            Object installedId = installed.get("id");
            if (installedId != null) {
                extraConfig.put("installedAgentId", String.valueOf(installedId));
            }
        }
        extraConfig.put("saveMode", "install");
    }

    private KnowledgeBaseEntity requireOwnedKnowledgeBase(UUID userId, String sourceId) {
        return knowledgeBaseRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(
                        parseUuid(sourceId, "knowledge sourceId is invalid"),
                        userId
                )
                .orElseThrow(() -> new IllegalArgumentException("knowledge base not found"));
    }

    private McpServerEntity requireEnabledMcpServer(String sourceId) {
        if (!isMcpUserCenterEnabled()) {
            throw new IllegalStateException("mcp user center is disabled");
        }
        McpServerEntity server = mcpServerRepository.findById(parseLong(sourceId, "mcp sourceId is invalid"))
                .orElseThrow(() -> new IllegalArgumentException("mcp source not found"));
        if (!server.isEnabled()) {
            throw new IllegalArgumentException("mcp source not found");
        }
        return server;
    }

    private LocalSkillDefinition requireAvailableSkill(String sourceId) {
        return localSkillDiscoveryService.getSkill(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("skill source not found"));
    }

    private UserSavedAssetEntity createSavedAsset(UUID userId, MarketAssetType assetType, String sourceId) {
        UserSavedAssetEntity entity = new UserSavedAssetEntity();
        entity.setUserId(userId);
        entity.setAssetType(assetType);
        entity.setSourceId(sourceId);
        return entity;
    }

    private boolean matchesSearch(Map<String, Object> item, String search) {
        String normalized = normalize(search);
        if (normalized == null) {
            return true;
        }
        String haystack = String.join(" ",
                stringify(item.get("title")),
                stringify(item.get("summary")),
                stringify(item.get("description")),
                stringify(item.get("category")),
                stringify(item.get("assetType")),
                stringify(item.get("sourceId")),
                stringify(item.get("tags")));
        return haystack.toLowerCase(Locale.ROOT).contains(normalized);
    }

    private boolean isMarketEnabled() {
        return sysConfigService.getBoolean(KEY_MARKET_ENABLED, true);
    }

    private boolean isMcpUserCenterEnabled() {
        return sysConfigService.getBoolean(KEY_MCP_USER_CENTER_ENABLED, true);
    }

    private void requireMarketEnabled() {
        if (!isMarketEnabled()) {
            throw new IllegalStateException("unified market is disabled");
        }
    }

    private static MarketAssetType parseOptionalAssetType(String raw) {
        return normalize(raw) == null ? null : MarketAssetType.fromString(raw);
    }

    private static UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Long parseLong(String raw, String message) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireSourceId(String raw) {
        String value = normalize(raw);
        if (value == null) {
            throw new IllegalArgumentException("sourceId is required");
        }
        return value;
    }

    private static String defaultString(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String firstNonBlank(Object first, Object second) {
        String firstValue = normalize(first);
        if (firstValue != null) {
            return firstValue;
        }
        String secondValue = normalize(second);
        return secondValue == null ? "" : secondValue;
    }

    private static String normalize(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static String savedKey(MarketAssetType assetType, String sourceId) {
        return assetType.name() + ":" + sourceId;
    }

    private static String stringify(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(Objects::toString).reduce((left, right) -> left + " " + right).orElse("");
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private static List<String> toTagList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(PlatformMarketService::normalize)
                    .filter(Objects::nonNull)
                    .toList();
        }
        String text = normalize(raw);
        if (text == null) {
            return List.of();
        }
        String[] parts = text.split("[,，]");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalize(part);
            if (normalized != null) {
                tags.add(normalized);
            }
        }
        return tags;
    }
}
