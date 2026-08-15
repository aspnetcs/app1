package com.webchat.adminapi.knowledge;

import com.webchat.platformapi.config.SysConfigService;
import com.webchat.platformapi.market.ContextAssetContract;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KnowledgeAdminService {

    private final Environment environment;
    private final SysConfigService sysConfigService;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeAdminService(Environment environment, SysConfigService sysConfigService, JdbcTemplate jdbcTemplate) {
        this.environment = environment;
        this.sysConfigService = sysConfigService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listBases() {
        return jdbcTemplate.query(
                """
                select
                    kb.id,
                    kb.owner_user_id,
                    kb.name,
                    kb.description,
                    kb.updated_at,
                    count(distinct kd.id) as document_count,
                    coalesce(sum(kd.chunk_count), 0) as chunk_count,
                    max(kj.completed_at) as last_ingest_at
                from knowledge_base kb
                left join knowledge_document kd on kd.base_id = kb.id and kd.deleted_at is null
                left join knowledge_ingest_job kj on kj.base_id = kb.id
                where kb.deleted_at is null
                group by kb.id, kb.owner_user_id, kb.name, kb.description, kb.updated_at
                order by kb.updated_at desc
                limit 200
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", rs.getString("id"));
                    payload.put("ownerUserId", rs.getString("owner_user_id"));
                    payload.put("name", defaultString(rs.getString("name")));
                    payload.put("description", defaultString(rs.getString("description")));
                    payload.put("status", rs.getLong("document_count") > 0 ? "READY" : "EMPTY");
                    payload.put("documentCount", rs.getLong("document_count"));
                    payload.put("chunkCount", rs.getLong("chunk_count"));
                    payload.put("retrievalMode", ContextAssetContract.USAGE_MODE_RETRIEVAL);
                    payload.put("lastIngestAt", rs.getTimestamp("last_ingest_at") == null ? null : rs.getTimestamp("last_ingest_at").toInstant().toString());
                    payload.put("updatedAt", rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant().toString());
                    payload.putAll(ContextAssetContract.knowledgeContractPayload());
                    return payload;
                }
        );
    }

    public List<Map<String, Object>> listJobs() {
        return jdbcTemplate.query(
                """
                select
                    kj.id,
                    kj.base_id,
                    kb.name as base_name,
                    kj.status,
                    kj.processed_chunks,
                    kj.error_message,
                    kj.created_at,
                    kj.started_at,
                    kj.completed_at
                from knowledge_ingest_job kj
                left join knowledge_base kb on kb.id = kj.base_id
                order by kj.created_at desc
                limit 100
                """,
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", rs.getString("id"));
                    payload.put("baseId", rs.getString("base_id"));
                    payload.put("baseName", defaultString(rs.getString("base_name")));
                    payload.put("status", defaultString(rs.getString("status")).toUpperCase());
                    payload.put("phase", resolvePhase(rs.getString("status")));
                    payload.put("totalDocuments", 0);
                    payload.put("processedDocuments", rs.getInt("processed_chunks"));
                    payload.put("errorMessage", defaultString(rs.getString("error_message")));
                    payload.put("updatedAt", rs.getTimestamp("completed_at") != null
                            ? rs.getTimestamp("completed_at").toInstant().toString()
                            : rs.getTimestamp("started_at") != null
                            ? rs.getTimestamp("started_at").toInstant().toString()
                            : rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant().toString());
                    return payload;
                }
        );
    }

    public Map<String, Object> config() {
        return Map.of(
                "enabled", readBoolean("platform.knowledge.enabled", true),
                "maxBasesPerUser", readInt(sysConfigService.get("knowledge.maxBasesPerUser").orElse(null),
                        environment.getProperty("platform.knowledge.max-bases-per-user", Integer.class, 12)),
                "maxChunksPerDocument", readInt(sysConfigService.get("knowledge.maxChunksPerDocument").orElse(null),
                        environment.getProperty("platform.knowledge.max-chunks-per-document", Integer.class, 200))
        );
    }

    public Map<String, Object> updateConfig(Map<String, Object> body) {
        int maxBasesPerUser = readInt(body == null ? null : body.get("maxBasesPerUser"),
                environment.getProperty("platform.knowledge.max-bases-per-user", Integer.class, 12));
        int maxChunksPerDocument = readInt(body == null ? null : body.get("maxChunksPerDocument"),
                environment.getProperty("platform.knowledge.max-chunks-per-document", Integer.class, 200));
        sysConfigService.set("knowledge.maxBasesPerUser", String.valueOf(maxBasesPerUser));
        sysConfigService.set("knowledge.maxChunksPerDocument", String.valueOf(maxChunksPerDocument));
        return config();
    }

    private static String resolvePhase(String status) {
        String normalizedStatus = defaultString(status).toLowerCase();
        if ("processing".equals(normalizedStatus)) {
            return "EMBEDDING";
        }
        if ("completed".equals(normalizedStatus)) {
            return "DONE";
        }
        if ("failed".equals(normalizedStatus)) {
            return "FAILED";
        }
        if ("pending".equals(normalizedStatus)) {
            return "QUEUED";
        }
        return normalizedStatus.isEmpty() ? "-" : normalizedStatus.toUpperCase();
    }

    private boolean readBoolean(String key, boolean fallback) {
        return environment.getProperty(key, Boolean.class, fallback);
    }

    private static int readInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String defaultString(Object value) {
        if (value == null) {
            return "";
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
