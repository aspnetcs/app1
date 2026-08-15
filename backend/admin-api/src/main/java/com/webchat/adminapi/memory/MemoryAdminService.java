package com.webchat.adminapi.memory;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryAdminService {

    private final SysConfigService sysConfigService;
    private final JdbcTemplate jdbcTemplate;

    public MemoryAdminService(SysConfigService sysConfigService, JdbcTemplate jdbcTemplate) {
        this.sysConfigService = sysConfigService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getConfig() {
        return Map.of(
                "enabled", getBoolean("platform.memory.enabled", false),
                "requireConsent", getBoolean("platform.memory.require-consent", true),
                "maxEntriesPerUser", getInt("platform.memory.max-entries-per-user", 50, 1),
                "maxCharsPerEntry", getInt("platform.memory.max-chars-per-entry", 1000, 32),
                "retentionDays", getInt("platform.memory.retention-days", 30, 1),
                "summaryModel", getString("platform.memory.summary-model", "")
        );
    }

    public Map<String, Object> updateConfig(Map<String, Object> body) {
        Map<String, Object> current = getConfig();
        boolean enabled = readBoolean(body, "enabled", (Boolean) current.get("enabled"));
        boolean requireConsent = readBoolean(body, "requireConsent", (Boolean) current.get("requireConsent"));
        int maxEntriesPerUser = readInt(body, "maxEntriesPerUser", (Integer) current.get("maxEntriesPerUser"), 1);
        int maxCharsPerEntry = readInt(body, "maxCharsPerEntry", (Integer) current.get("maxCharsPerEntry"), 32);
        int retentionDays = readInt(body, "retentionDays", (Integer) current.get("retentionDays"), 1);
        String summaryModel = readString(body, "summaryModel", (String) current.get("summaryModel"));

        sysConfigService.set("platform.memory.enabled", String.valueOf(enabled));
        sysConfigService.set("platform.memory.require-consent", String.valueOf(requireConsent));
        sysConfigService.set("platform.memory.max-entries-per-user", String.valueOf(maxEntriesPerUser));
        sysConfigService.set("platform.memory.max-chars-per-entry", String.valueOf(maxCharsPerEntry));
        sysConfigService.set("platform.memory.retention-days", String.valueOf(retentionDays));
        sysConfigService.set("platform.memory.summary-model", summaryModel);
        return getConfig();
    }

    public Map<String, Object> getStats() {
        long totalUsers = queryForLong("select count(*) from memory_consent");
        long totalEntries = queryForLong("select count(*) from memory_entry");
        long pendingReviews = queryForLong("select count(*) from memory_audit where status = 'pending'");
        double averageEntriesPerUser = totalUsers == 0 ? 0D : Math.round(((double) totalEntries / (double) totalUsers) * 100.0D) / 100.0D;
        return Map.of(
                "enabled", getBoolean("platform.memory.enabled", false),
                "totalUsers", totalUsers,
                "totalEntries", totalEntries,
                "pendingReviews", pendingReviews,
                "averageEntriesPerUser", averageEntriesPerUser
        );
    }

    public Map<String, Object> getAudits(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        long total = queryForLong("select count(*) from memory_audit");
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select id, user_id, action, summary, status, created_at
                from memory_audit
                order by created_at desc
                limit ? offset ?
                """,
                ps -> {
                    ps.setInt(1, safeSize);
                    ps.setInt(2, safePage * safeSize);
                },
                this::mapAuditRow
        );
        return Map.of(
                "items", items,
                "total", total,
                "page", safePage,
                "size", safeSize
        );
    }

    private Map<String, Object> mapAuditRow(ResultSet rs, int rowNum) throws SQLException {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", String.valueOf(rs.getObject("id")));
        item.put("userId", String.valueOf(rs.getObject("user_id")));
        item.put("action", rs.getString("action"));
        item.put("summary", rs.getString("summary"));
        item.put("status", rs.getString("status"));
        item.put("createdAt", rs.getTimestamp("created_at") == null ? "" : rs.getTimestamp("created_at").toInstant().toString());
        return item;
    }

    private long queryForLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        return "true".equalsIgnoreCase(sysConfigService.getOrDefault(key, String.valueOf(defaultValue)));
    }

    private int getInt(String key, int defaultValue, int min) {
        try {
            return Math.max(min, Integer.parseInt(sysConfigService.getOrDefault(key, String.valueOf(defaultValue))));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String getString(String key, String defaultValue) {
        String value = sysConfigService.getOrDefault(key, defaultValue);
        return value == null ? "" : value.trim();
    }

    private static boolean readBoolean(Map<String, Object> body, String key, boolean defaultValue) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return defaultValue;
        }
        Object value = body.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static int readInt(Map<String, Object> body, String key, int defaultValue, int min) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return defaultValue;
        }
        try {
            return Math.max(min, Integer.parseInt(String.valueOf(body.get(key))));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String readString(Map<String, Object> body, String key, String defaultValue) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(body.get(key)).trim();
        return value.isEmpty() ? "" : value;
    }
}
