package com.webchat.adminapi.dashboard;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class DashboardController {

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        Instant now = Instant.now();
        Instant startOfToday = now.truncatedTo(ChronoUnit.DAYS);
        Instant startOfWeek = startOfToday.minus(6, ChronoUnit.DAYS);
        Instant startOfMonth = startOfToday.minus(29, ChronoUnit.DAYS);

        long todayConversations = countConversations(startOfToday);
        long weekConversations = countConversations(startOfWeek);
        long monthConversations = countConversations(startOfMonth);

        List<Map<String, Object>> modelRanking = jdbc.queryForList("""
                SELECT COALESCE(model, '') AS model,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens
                FROM ai_usage_log
                WHERE created_at >= ?
                GROUP BY model
                ORDER BY request_count DESC, total_tokens DESC
                LIMIT 10
                """, Timestamp.from(startOfMonth));

        List<Map<String, Object>> userRanking = jdbc.queryForList("""
                SELECT CAST(user_id AS text) AS user_id,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens
                FROM ai_usage_log
                WHERE created_at >= ?
                GROUP BY user_id
                ORDER BY request_count DESC, total_tokens DESC
                LIMIT 10
                """, Timestamp.from(startOfMonth));

        List<Map<String, Object>> tokenTrend = jdbc.queryForList("""
                SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS day,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COUNT(*) AS request_count
                FROM ai_usage_log
                WHERE created_at >= ?
                GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD')
                ORDER BY day ASC
                """, Timestamp.from(startOfMonth));

        return ApiResponse.ok(new LinkedHashMap<>(Map.of(
                "todayConversations", todayConversations,
                "weekConversations", weekConversations,
                "monthConversations", monthConversations,
                "modelRanking", modelRanking,
                "userRanking", userRanking,
                "tokenTrend", tokenTrend
        )));
    }

    private long countConversations(Instant from) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ai_conversation
                WHERE deleted_at IS NULL
                  AND is_temporary = FALSE
                  AND created_at >= ?
                """, Long.class, Timestamp.from(from));
        return count == null ? 0L : count;
    }
}



