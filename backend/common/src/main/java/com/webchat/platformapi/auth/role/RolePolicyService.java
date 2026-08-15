package com.webchat.platformapi.auth.role;

import com.webchat.platformapi.auth.group.UserGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Merges role-level policy defaults with group-level overrides.
 * Priority: group > role-policy > global fallback.
 */
@Service
public class RolePolicyService {

    private static final Logger log = LoggerFactory.getLogger(RolePolicyService.class);
    private static final String DAILY_KEY_PREFIX = "quota:daily:";
    private static final String GUEST_ROLE = "guest";

    private final RolePolicyProperties properties;
    private final UserGroupService userGroupService;
    private final StringRedisTemplate redis;

    public RolePolicyService(RolePolicyProperties properties,
                             UserGroupService userGroupService,
                             StringRedisTemplate redis) {
        this.properties = properties;
        this.userGroupService = userGroupService;
        this.redis = redis;
    }

    /**
     * Resolve the effective allowed model set for a user.
     * If group has non-empty allowedModels, use group set (narrower).
     * Otherwise fall back to role policy.
     * Empty result = all models visible.
     */
    public Set<String> resolveAllowedModels(UUID userId, String role) {
        if (isGuestRole(role)) {
            return Set.of();
        }

        // Group takes priority
        UserGroupService.GroupProfile profile = userGroupService.resolveProfile(userId);
        if (!profile.allowedModels().isEmpty()) {
            return new LinkedHashSet<>(profile.allowedModels());
        }

        // Fall back to role policy
        RolePolicyProperties.RoleConfig config = properties.getPolicy(normalizeRole(role));
        String models = config.getAllowedModels();
        if (models == null || models.isBlank()) {
            return Set.of(); // empty = all models
        }
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Resolve the effective per-minute rate limit.
     * Takes the more restrictive of group chatRateLimitPerMinute and role policy.
     * Group value of null/0 = no override (use role policy).
     */
    public int resolveRateLimit(UUID userId, String role) {
        if (isGuestRole(role)) {
            return 0;
        }

        RolePolicyProperties.RoleConfig config = properties.getPolicy(normalizeRole(role));
        int roleLimit = config.getRateLimitPerMinute();

        UserGroupService.GroupProfile profile = userGroupService.resolveProfile(userId);
        Integer groupLimit = profile.chatRateLimitPerMinute();
        if (groupLimit != null && groupLimit > 0) {
            return Math.min(groupLimit, roleLimit);
        }
        return roleLimit;
    }

    /**
     * Returns the daily chat quota for a role. 0 = unlimited.
     * This is role-only (group does not override).
     */
    public int resolveDailyQuota(String role) {
        if (isGuestRole(role)) {
            return 0;
        }
        return properties.getPolicy(normalizeRole(role)).getDailyChatQuota();
    }

    /**
     * Returns the per-model rate limit for a role. 0 = disabled.
     * This is role-only (group does not override).
     */
    public int resolveModelRateLimit(String role) {
        if (isGuestRole(role)) {
            return 0;
        }
        return properties.getPolicy(normalizeRole(role)).getModelRateLimitPerMinute();
    }

    private boolean isGuestRole(String role) {
        return GUEST_ROLE.equals(normalizeRole(role));
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        return "pending".equals(normalized) ? "user" : normalized;
    }

    /**
     * Read current daily usage count from Redis (read-only).
     */
    public long getDailyUsageCount(UUID userId) {
        try {
            String key = dailyKey(userId);
            String value = redis.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            log.warn("[role-policy] failed to read daily usage: userId={}, error={}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * Increment daily usage count after a successful chat.
     * Sets 48h TTL on first creation.
     */
    public void incrementDailyUsage(UUID userId) {
        try {
            String key = dailyKey(userId);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofHours(48));
            }
        } catch (Exception e) {
            // fail-open: do not block the user if Redis is down
            log.warn("[role-policy] failed to increment daily usage: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * Check if the user has exceeded their daily quota.
     * Returns true if still within quota (or quota is unlimited).
     */
    public boolean isDailyQuotaAvailable(UUID userId, String role) {
        int quota = resolveDailyQuota(role);
        if (quota <= 0) {
            return true; // unlimited
        }
        long used = getDailyUsageCount(userId);
        return used < quota;
    }

    private String dailyKey(UUID userId) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        return DAILY_KEY_PREFIX + userId + ":" + today;
    }
}
