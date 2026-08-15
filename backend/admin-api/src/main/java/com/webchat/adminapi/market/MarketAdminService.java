package com.webchat.adminapi.market;

import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.ai.extension.McpServerEntity;
import com.webchat.platformapi.ai.extension.McpServerRepository;
import com.webchat.platformapi.market.ContextAssetContract;
import com.webchat.platformapi.market.MarketAssetType;
import com.webchat.platformapi.market.MarketCatalogService;
import com.webchat.platformapi.skill.LocalSkillDiscoveryService;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MarketAdminService {

    private final MarketCatalogService marketCatalogService;
    private final AgentService agentService;
    private final McpServerRepository mcpServerRepository;
    private final LocalSkillDiscoveryService localSkillDiscoveryService;
    private final JdbcTemplate jdbcTemplate;

    public MarketAdminService(
            MarketCatalogService marketCatalogService,
            AgentService agentService,
            McpServerRepository mcpServerRepository,
            LocalSkillDiscoveryService localSkillDiscoveryService,
            JdbcTemplate jdbcTemplate
    ) {
        this.marketCatalogService = marketCatalogService;
        this.agentService = agentService;
        this.mcpServerRepository = mcpServerRepository;
        this.localSkillDiscoveryService = localSkillDiscoveryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listCatalog(String assetTypeRaw, String keyword) {
        MarketAssetType assetType = parseOptionalAssetType(assetTypeRaw);
        String normalizedKeyword = normalizeKeyword(keyword);
        List<Map<String, Object>> items = marketCatalogService.listCatalogItems(false).stream()
                .filter(item -> assetType == null || item.getAssetType() == assetType)
                .map(marketCatalogService::toCatalogPayload)
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .toList();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("total", items.size());
        return payload;
    }

    public Map<String, Object> createCatalogItem(Map<String, Object> body) {
        return marketCatalogService.toCatalogPayload(marketCatalogService.createCatalogItem(body));
    }

    public Map<String, Object> updateCatalogItem(UUID id, Map<String, Object> body) {
        return marketCatalogService.toCatalogPayload(marketCatalogService.updateCatalogItem(id, body));
    }

    public List<Map<String, Object>> listSourceOptions(String assetTypeRaw) {
        MarketAssetType assetType = parseOptionalAssetType(assetTypeRaw);
        if (assetType != null) {
            return listSourceOptionsByType(assetType);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (MarketAssetType type : MarketAssetType.values()) {
            items.addAll(listSourceOptionsByType(type));
        }
        return items;
    }

    private List<Map<String, Object>> listSourceOptionsByType(MarketAssetType assetType) {
        return switch (assetType) {
            case AGENT -> listAgentSourceOptions();
            case KNOWLEDGE -> listKnowledgeSourceOptions();
            case MCP -> listMcpSourceOptions();
            case SKILL -> listSkillSourceOptions();
        };
    }

    private List<Map<String, Object>> listAgentSourceOptions() {
        Page<Map<String, Object>> page = agentService.listForAdmin(null, null, null, "SYSTEM", null, 0, 100);
        return page.getContent().stream().map(item -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("assetType", MarketAssetType.AGENT.name());
            payload.put("sourceId", normalizeNullableString(item.get("id")));
            payload.put("title", defaultString(item.get("name")));
            payload.put("summary", defaultString(item.get("description")));
            payload.put("category", defaultString(item.get("category")));
            payload.put("cover", defaultString(item.get("avatar")));
            payload.put("enabled", !Boolean.FALSE.equals(item.get("enabled")));
            payload.put("source", item);
            return payload;
        }).toList();
    }

    private List<Map<String, Object>> listKnowledgeSourceOptions() {
        return jdbcTemplate.query(
                """
                select id, owner_user_id, name, description, updated_at
                from knowledge_base
                where deleted_at is null
                order by updated_at desc
                limit 200
                """,
                (rs, rowNum) -> toKnowledgeSourcePayload(rs)
        );
    }

    private List<Map<String, Object>> listMcpSourceOptions() {
        return mcpServerRepository.findAll().stream().map(server -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("assetType", MarketAssetType.MCP.name());
            payload.put("sourceId", String.valueOf(server.getId()));
            payload.put("title", defaultString(server.getName()));
            payload.put("summary", defaultString(server.getDescription()));
            payload.put("category", defaultString(server.getTransportType()));
            payload.put("enabled", server.isEnabled());
            payload.put("source", Map.of(
                    "id", String.valueOf(server.getId()),
                    "name", defaultString(server.getName()),
                    "description", defaultString(server.getDescription()),
                    "transportType", defaultString(server.getTransportType()),
                    "enabled", server.isEnabled()
            ));
            return payload;
        }).toList();
    }

    private List<Map<String, Object>> listSkillSourceOptions() {
        return localSkillDiscoveryService.listSkills().stream().<Map<String, Object>>map(skill -> {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
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
            payload.put("assetType", MarketAssetType.SKILL.name());
            payload.put("sourceId", skill.sourceId());
            payload.put("title", skill.name());
            payload.put("summary", skill.description());
            payload.put("category", "skill");
            payload.put("enabled", true);
            payload.putAll(ContextAssetContract.skillContractPayload());
            payload.put("source", source);
            return payload;
        }).toList();
    }

    private Map<String, Object> toKnowledgeSourcePayload(ResultSet rs) throws SQLException {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        LinkedHashMap<String, Object> source = new LinkedHashMap<>(ContextAssetContract.knowledgeContractPayload());
        source.put("id", rs.getString("id"));
        source.put("ownerUserId", rs.getString("owner_user_id"));
        source.put("name", defaultString(rs.getString("name")));
        source.put("description", defaultString(rs.getString("description")));
        source.put("updatedAt", rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant().toString());
        payload.put("assetType", MarketAssetType.KNOWLEDGE.name());
        payload.put("sourceId", rs.getString("id"));
        payload.put("title", defaultString(rs.getString("name")));
        payload.put("summary", defaultString(rs.getString("description")));
        payload.put("category", "knowledge");
        payload.put("enabled", true);
        payload.putAll(ContextAssetContract.knowledgeContractPayload());
        payload.put("source", source);
        return payload;
    }

    private static boolean matchesKeyword(Map<String, Object> item, String keyword) {
        if (keyword == null) {
            return true;
        }
        return containsKeyword(item.get("title"), keyword)
                || containsKeyword(item.get("summary"), keyword)
                || containsKeyword(item.get("description"), keyword)
                || containsKeyword(item.get("category"), keyword)
                || containsKeyword(item.get("tags"), keyword);
    }

    private static boolean containsKeyword(Object raw, String keyword) {
        return defaultString(raw).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static MarketAssetType parseOptionalAssetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return MarketAssetType.fromString(raw);
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
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
}
