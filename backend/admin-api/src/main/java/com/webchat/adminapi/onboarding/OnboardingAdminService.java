package com.webchat.adminapi.onboarding;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OnboardingAdminService {

    private static final List<String> DEFAULT_STEPS = List.of("welcome", "history", "market", "settings");

    private final JdbcTemplate jdbcTemplate;
    private final SysConfigService sysConfigService;

    public OnboardingAdminService(JdbcTemplate jdbcTemplate, SysConfigService sysConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sysConfigService = sysConfigService;
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", readBoolean("platform.onboarding.enabled", true));
        result.put("allowSkip", readBoolean("platform.onboarding.allow-skip", true));
        result.put("welcomeTitle", sysConfigService.getOrDefault("platform.onboarding.welcome-title", "欢迎使用 AI App"));
        result.put("welcomeMessage", sysConfigService.getOrDefault("platform.onboarding.welcome-message", "通过简短引导了解历史、助手市场和常用入口。"));
        result.put("steps", parseSteps(sysConfigService.getOrDefault("platform.onboarding.steps", String.join(",", DEFAULT_STEPS))));
        return result;
    }

    public Map<String, Object> updateConfig(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("config body required");
        }
        sysConfigService.set("platform.onboarding.enabled", String.valueOf(Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", true)))));
        sysConfigService.set("platform.onboarding.allow-skip", String.valueOf(Boolean.parseBoolean(String.valueOf(body.getOrDefault("allowSkip", true)))));
        sysConfigService.set("platform.onboarding.welcome-title", text(body.get("welcomeTitle"), "欢迎使用 AI App"));
        sysConfigService.set("platform.onboarding.welcome-message", text(body.get("welcomeMessage"), "通过简短引导了解历史、助手市场和常用入口。"));
        sysConfigService.set("platform.onboarding.steps", String.join(",", parseSteps(body.get("steps"))));
        return getConfig();
    }

    public Map<String, Object> getUserState(UUID userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select user_id, status, current_step, completed_steps, reset_count, last_completed_at, skipped_at, created_at, updated_at from ai_user_onboarding_state where user_id = ?",
                userId
        );
        if (rows.isEmpty()) {
            return Map.of(
                    "userId", userId.toString(),
                    "status", "not_started",
                    "currentStep", DEFAULT_STEPS.get(0),
                    "completedSteps", List.of(),
                    "resetCount", 0
            );
        }

        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId.toString());
        result.put("status", text(row.get("status"), "not_started"));
        result.put("currentStep", text(row.get("current_step"), DEFAULT_STEPS.get(0)));
        result.put("completedSteps", parseStoredSteps(row.get("completed_steps")));
        result.put("resetCount", number(row.get("reset_count")));
        result.put("lastCompletedAt", iso(row.get("last_completed_at")));
        result.put("skippedAt", iso(row.get("skipped_at")));
        result.put("createdAt", iso(row.get("created_at")));
        result.put("updatedAt", iso(row.get("updated_at")));
        return result;
    }

    public Map<String, Object> resetUserState(UUID userId) {
        jdbcTemplate.update(
                """
                insert into ai_user_onboarding_state (
                    user_id,
                    status,
                    current_step,
                    completed_steps,
                    reset_count,
                    last_completed_at,
                    skipped_at,
                    created_at,
                    updated_at
                ) values (?, 'reset', ?, '', 1, null, null, now(), now())
                on conflict (user_id) do update set
                    status = 'reset',
                    current_step = ?,
                    completed_steps = '',
                    reset_count = ai_user_onboarding_state.reset_count + 1,
                    last_completed_at = null,
                    skipped_at = null,
                    updated_at = now()
                """,
                userId,
                DEFAULT_STEPS.get(0),
                DEFAULT_STEPS.get(0)
        );
        return getUserState(userId);
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(sysConfigService.getOrDefault(key, String.valueOf(defaultValue)));
    }

    private static List<String> parseSteps(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw);
        List<String> values = java.util.Arrays.stream(text.split("[\\r\\n,]"))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        return values.isEmpty() ? DEFAULT_STEPS : values;
    }

    private static List<String> parseStoredSteps(Object raw) {
        if (raw == null) {
            return List.of();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split("[\\r\\n,]"))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static String text(Object raw, String defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private static int number(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    private static String iso(Object raw) {
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (raw instanceof Instant instant) {
            return instant.toString();
        }
        return raw == null ? null : String.valueOf(raw);
    }
}