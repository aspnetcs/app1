package com.webchat.adminapi.user;

import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserAdminService {

    private static final List<String> ALLOWED_ROLES = List.of("user", "admin", "guest", "premium", "pending");

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final RolePolicyProperties rolePolicyProperties;
    private final RolePolicyService rolePolicyService;

    public UserAdminService(UserRepository userRepository, JdbcTemplate jdbc,
                            RolePolicyProperties rolePolicyProperties,
                            RolePolicyService rolePolicyService) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.rolePolicyProperties = rolePolicyProperties;
        this.rolePolicyService = rolePolicyService;
    }

    public ApiResponse<Map<String, Object>> list(int page, int size, String search, String roleFilter) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);

        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (phone ILIKE ? OR email ILIKE ? OR id::text ILIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (roleFilter != null && !roleFilter.isBlank()) {
            sql.append(" AND role = ?");
            params.add(roleFilter.trim());
        }

        String countSql = sql.toString().replaceFirst("SELECT \\*", "SELECT COUNT(*)");
        long total = jdbc.queryForObject(countSql, Long.class, params.toArray());

        sql.append(" ORDER BY CASE WHEN deleted_at IS NULL THEN 0 ELSE 1 END, created_at DESC LIMIT ? OFFSET ?");
        params.add(s);
        params.add(p * s);

        List<Map<String, Object>> users = jdbc.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> user : users) {
            out.add(toListItem(user));
        }

        return ApiResponse.ok(Map.of(
                "items", out,
                "total", total,
                "page", p,
                "size", s
        ));
    }

    public ApiResponse<Map<String, Object>> detail(UUID id) {
        return userRepository.findById(id)
                .map(user -> ApiResponse.ok(toDetailItem(user)))
                .orElse(ApiResponse.error(ErrorCodes.SERVER_ERROR, "用户不存在"));
    }

    public ApiResponse<Map<String, Object>> update(UUID id, Map<String, Object> body) {
        UserEntity user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "用户不存在");
        }

        boolean revokeSessions = false;

        if (body.containsKey("role")) {
            String newRole = String.valueOf(body.get("role")).trim();
            if (!ALLOWED_ROLES.contains(newRole)) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "role 参数错误");
            }
            if (!Objects.equals(user.getRole(), newRole)) {
                user.setRole(newRole);
                revokeSessions = true;
            }
        }

        if (body.containsKey("tokenQuota")) {
            Long quota = parseTokenQuota(body.get("tokenQuota"));
            if (quota == null) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "tokenQuota 参数错误");
            }
            user.setTokenQuota(quota);
        }

        if (body.containsKey("status")) {
            Boolean disabled = parseStatusFlag(body.get("status"));
            if (disabled == null) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "status 参数错误");
            }
            boolean changed = applyDisabledState(user, disabled);
            revokeSessions = revokeSessions || (changed && disabled);
        }

        if (body.containsKey("deletedAt")) {
            Boolean disabled = parseBanFlag(body.get("deletedAt"));
            if (disabled == null) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "deletedAt 参数错误");
            }
            boolean changed = applyDisabledState(user, disabled);
            revokeSessions = revokeSessions || (changed && disabled);
        }

        userRepository.save(user);
        if (revokeSessions) {
            revokeActiveSessions(id);
        }
        return ApiResponse.ok("已更新", toDetailItem(user));
    }

    /**
     * Reset a user's token usage counter back to zero.
     */
    public ApiResponse<Map<String, Object>> resetTokenUsage(UUID id) {
        UserEntity user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "用户不存在");
        }
        user.setTokenUsed(0);
        userRepository.save(user);
        return ApiResponse.ok("已重置", toDetailItem(user));
    }

    /**
     * Aggregate stats in a single SQL query for better performance.
     */
    public ApiResponse<Map<String, Object>> stats() {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Map<String, Object> row = jdbc.queryForMap(
                """
                SELECT
                    COUNT(*)                                                    AS total,
                    COUNT(*) FILTER (WHERE created_at >= ?)                     AS today_new,
                    COUNT(*) FILTER (WHERE role = 'admin')                      AS admins,
                    COUNT(*) FILTER (WHERE role = 'guest')                      AS guests,
                    COUNT(*) FILTER (WHERE role = 'premium')                    AS premiums,
                    COUNT(*) FILTER (WHERE role = 'pending')                    AS pendings,
                    COUNT(*) FILTER (WHERE deleted_at IS NOT NULL)              AS disabled
                FROM users
                """,
                java.sql.Timestamp.from(today)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", row.get("total"));
        result.put("todayNew", row.get("today_new"));
        result.put("admins", row.get("admins"));
        result.put("guests", row.get("guests"));
        result.put("premiums", row.get("premiums"));
        result.put("pendings", row.get("pendings"));
        result.put("disabled", row.get("disabled"));
        return ApiResponse.ok(result);
    }

    private Map<String, Object> toListItem(Map<String, Object> user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", user.get("id"));
        item.put("phone", user.get("phone"));
        item.put("email", user.get("email"));
        item.put("avatar", user.get("avatar"));
        item.put("role", user.get("role"));
        item.put("status", toStatus(user.get("deleted_at")));
        item.put("tokenQuota", user.get("token_quota"));
        item.put("tokenUsed", user.get("token_used"));
        item.put("createdAt", user.get("created_at"));
        item.put("updatedAt", user.get("updated_at"));
        return item;
    }

    private Map<String, Object> toDetailItem(UserEntity user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("phone", user.getPhone());
        map.put("email", user.getEmail());
        map.put("avatar", user.getAvatar());
        map.put("role", user.getRole());
        map.put("status", toStatus(user.getDeletedAt()));
        map.put("tokenQuota", user.getTokenQuota());
        map.put("tokenUsed", user.getTokenUsed());
        map.put("deletedAt", user.getDeletedAt());
        String role = user.getRole() == null ? "user" : user.getRole();
        int dailyQuotaLimit = rolePolicyProperties.getPolicy(role).getDailyChatQuota();
        long dailyQuotaUsed = rolePolicyService.getDailyUsageCount(user.getId());
        map.put("dailyQuotaLimit", dailyQuotaLimit);
        map.put("dailyQuotaUsed", dailyQuotaUsed);
        map.put("createdAt", user.getCreatedAt());
        map.put("updatedAt", user.getUpdatedAt());
        return map;
    }

    private boolean applyDisabledState(UserEntity user, boolean disabled) {
        boolean currentlyDisabled = user.getDeletedAt() != null;
        if (currentlyDisabled == disabled) {
            return false;
        }
        user.setDeletedAt(disabled ? Instant.now() : null);
        return true;
    }

    private void revokeActiveSessions(UUID targetUserId) {
        jdbc.update(
                """
                UPDATE device_session
                   SET revoked_at = NOW()
                 WHERE user_id = ?
                   AND revoked_at IS NULL
                   AND (expires_at IS NULL OR expires_at > NOW())
                """,
                targetUserId
        );
    }

    private static String toStatus(Object deletedAt) {
        return deletedAt == null ? "active" : "disabled";
    }

    static Long parseTokenQuota(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Boolean parseStatusFlag(Object raw) {
        if (raw == null) {
            return false;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return false;
        }
        if ("active".equalsIgnoreCase(text) || "enabled".equalsIgnoreCase(text) || "normal".equalsIgnoreCase(text)) {
            return false;
        }
        if ("disabled".equalsIgnoreCase(text) || "banned".equalsIgnoreCase(text) || "blocked".equalsIgnoreCase(text)) {
            return true;
        }
        return null;
    }

    static Boolean parseBanFlag(Object raw) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            int value = number.intValue();
            if (value == 0) {
                return false;
            }
            if (value == 1) {
                return true;
            }
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        return null;
    }
}
