package com.webchat.adminapi.audit;

import com.webchat.platformapi.audit.*;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
@RequestMapping("/api/v1/admin/audit")
public class AuditAdminController {

    private final JdbcTemplate jdbc;

    public AuditAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> logs(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userIdFilter,
            @RequestParam(defaultValue = "24") int hours
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足");

        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        int h = Math.min(Math.max(1, hours), 24 * 30);

        Instant from = Instant.now().minus(h, ChronoUnit.HOURS);
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE created_at >= ?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.from(from));

        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            params.add(action.trim());
        }
        if (userIdFilter != null && !userIdFilter.isBlank()) {
            try {
                UUID uid = UUID.fromString(userIdFilter.trim());
                sql.append(" AND user_id = ?");
                params.add(uid);
            } catch (IllegalArgumentException ignored) {}
        }

        // Count
        String countSql = sql.toString().replaceFirst("SELECT \\*", "SELECT COUNT(*)");
        long total = jdbc.queryForObject(countSql, Long.class, params.toArray());

        // Page
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(s);
        params.add(p * s);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());

        return ApiResponse.ok(Map.of(
                "items", rows,
                "total", total,
                "page", p,
                "size", s
        ));
    }

    @GetMapping("/stats")
    public ApiResponse<List<Map<String, Object>>> stats(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "24") int hours
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足");

        int h = Math.min(Math.max(1, hours), 24 * 30);
        Instant from = Instant.now().minus(h, ChronoUnit.HOURS);

        List<Map<String, Object>> stats = jdbc.queryForList(
                "SELECT action, COUNT(*) as count FROM audit_log WHERE created_at >= ? GROUP BY action ORDER BY count DESC",
                Timestamp.from(from)
        );

        return ApiResponse.ok(stats);
    }

    @GetMapping("/actions")
    public ApiResponse<List<String>> actions(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足");

        List<String> actions = jdbc.queryForList(
                "SELECT DISTINCT action FROM audit_log ORDER BY action",
                String.class
        );

        return ApiResponse.ok(actions);
    }
}




