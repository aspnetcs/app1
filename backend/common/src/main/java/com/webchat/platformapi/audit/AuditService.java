package com.webchat.platformapi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void log(UUID userId, String action, Map<String, Object> detail, String ip, String userAgent) {
        if (action == null || action.isBlank()) return;
        String safeAction = action.trim();
        if (safeAction.length() > 64) safeAction = safeAction.substring(0, 64);

        String json = "{}";
        try {
            json = objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (Exception e) {
            log.warn("[audit] serialize failed: action={}, userId={}, error={}", safeAction, userId, e.toString());
        }

        try {
            jdbc.update(
                    "INSERT INTO audit_log (user_id, action, detail, ip, user_agent, created_at) VALUES (?, ?, ?::jsonb, ?, ?, NOW())",
                    userId,
                    safeAction,
                    json,
                    emptyToNull(ip),
                    emptyToNull(userAgent)
            );
        } catch (Exception e) {
            log.warn("[audit] insert failed: action={}, userId={}, error={}", safeAction, userId, e.toString());
        }
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
