package com.webchat.platformapi.onboarding;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingService {

    private static final Set<String> ALLOWED_STATUS = Set.of(
            "not_started",
            "in_progress",
            "completed",
            "skipped",
            "reset"
    );

    static final String CONFIG_ENABLED = "platform.onboarding.enabled";
    static final String CONFIG_ALLOW_SKIP = "platform.onboarding.allow-skip";
    static final String CONFIG_WELCOME_TITLE = "platform.onboarding.welcome-title";
    static final String CONFIG_WELCOME_MESSAGE = "platform.onboarding.welcome-message";
    static final String CONFIG_STEPS = "platform.onboarding.steps";

    private static final List<String> DEFAULT_STEPS = List.of("welcome", "history", "market", "settings");

    private final JdbcTemplate jdbcTemplate;
    private final SysConfigService sysConfigService;

    public OnboardingService(JdbcTemplate jdbcTemplate, SysConfigService sysConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sysConfigService = sysConfigService;
    }

    public Map<String, Object> getState(UUID userId) {
        Map<String, Object> config = getConfig();
        Map<String, Object> row = findStateRow(userId);
        List<String> steps = readConfigSteps(config.get("steps"));
        String status = row == null ? "not_started" : text(row.get("status"), "not_started");
        String currentStep = row == null
                ? steps.get(0)
                : text(row.get("current_step"), steps.get(0));
        List<String> completedSteps = row == null
                ? List.of()
                : parseStoredSteps(row.get("completed_steps"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId.toString());
        result.put("status", status);
        result.put("currentStep", currentStep);
        result.put("completedSteps", completedSteps);
        result.put("resetCount", row == null ? 0 : number(row.get("reset_count")));
        result.put("lastCompletedAt", row == null ? null : iso(row.get("last_completed_at")));
        result.put("skippedAt", row == null ? null : iso(row.get("skipped_at")));
        result.put("createdAt", row == null ? null : iso(row.get("created_at")));
        result.put("updatedAt", row == null ? null : iso(row.get("updated_at")));
        result.put("config", config);
        result.put("shouldShow", Boolean.TRUE.equals(config.get("enabled")) && !Set.of("completed", "skipped").contains(status));
        return result;
    }

    public Map<String, Object> updateState(UUID userId, Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("body is required");
        }
        String status = normalizeStatus(body.get("status"));
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }

        List<String> steps = readConfigSteps(getConfig().get("steps"));
        String currentStep = normalizeCurrentStep(body.get("currentStep"), steps);
        List<String> completedSteps = normalizeCompletedSteps(body.get("completedSteps"), steps);
        Instant now = Instant.now();

        Integer resetCount = jdbcTemplate.query(
                "select reset_count from ai_user_onboarding_state where user_id = ?",
                rs -> rs.next() ? rs.getInt("reset_count") : 0,
                userId
        );

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
                ) values (?, ?, ?, ?, ?, ?, ?, now(), now())
                on conflict (user_id) do update set
                    status = excluded.status,
                    current_step = excluded.current_step,
                    completed_steps = excluded.completed_steps,
                    reset_count = excluded.reset_count,
                    last_completed_at = excluded.last_completed_at,
                    skipped_at = excluded.skipped_at,
                    updated_at = now()
                """,
                userId,
                status,
                currentStep,
                String.join(",", completedSteps),
                resetCount == null ? 0 : resetCount,
                completedAt(status, now),
                skippedAt(status, now)
        );
        return getState(userId);
    }

    public Map<String, Object> resetState(UUID userId) {
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
        return getState(userId);
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", readBoolean(CONFIG_ENABLED, true));
        config.put("allowSkip", readBoolean(CONFIG_ALLOW_SKIP, true));
        config.put("welcomeTitle", sysConfigService.getOrDefault(CONFIG_WELCOME_TITLE, "欢迎使用 AI App"));
        config.put("welcomeMessage", sysConfigService.getOrDefault(CONFIG_WELCOME_MESSAGE, "通过简短引导了解历史、助手市场和常用入口。"));
        config.put("steps", readSteps(sysConfigService.getOrDefault(CONFIG_STEPS, String.join(",", DEFAULT_STEPS))));
        return config;
    }

    public void updateConfig(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("config body required");
        }
        sysConfigService.set(CONFIG_ENABLED, String.valueOf(Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", true)))));
        sysConfigService.set(CONFIG_ALLOW_SKIP, String.valueOf(Boolean.parseBoolean(String.valueOf(body.getOrDefault("allowSkip", true)))));
        sysConfigService.set(CONFIG_WELCOME_TITLE, text(body.get("welcomeTitle"), "欢迎使用 AI App"));
        sysConfigService.set(CONFIG_WELCOME_MESSAGE, text(body.get("welcomeMessage"), "通过简短引导了解历史、助手市场和常用入口。"));
        sysConfigService.set(CONFIG_STEPS, String.join(",", normalizeCompletedSteps(body.get("steps"), DEFAULT_STEPS)));
    }

    private Map<String, Object> findStateRow(UUID userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select user_id, status, current_step, completed_steps, reset_count, last_completed_at, skipped_at, created_at, updated_at from ai_user_onboarding_state where user_id = ?",
                userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(sysConfigService.getOrDefault(key, String.valueOf(defaultValue)));
    }

    private static String normalizeStatus(Object raw) {
        String value = raw == null ? null : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!ALLOWED_STATUS.contains(value)) {
            throw new IllegalArgumentException("status is invalid");
        }
        return value;
    }

    private static String normalizeCurrentStep(Object raw, List<String> steps) {
        String value = raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return steps.get(0);
        }
        return steps.contains(value) ? value : steps.get(0);
    }

    private static List<String> normalizeCompletedSteps(Object raw, List<String> steps) {
        List<String> parsed = readSteps(raw);
        List<String> result = new ArrayList<>();
        for (String step : parsed) {
            if (!steps.contains(step) || result.contains(step)) {
                continue;
            }
            result.add(step);
        }
        return result;
    }

    private static List<String> readSteps(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String value = item == null ? "" : String.valueOf(item).trim().toLowerCase(Locale.ROOT);
                if (!value.isBlank() && !result.contains(value)) {
                    result.add(value);
                }
            }
            return result.isEmpty() ? DEFAULT_STEPS : result;
        }

        String text = raw == null ? "" : String.valueOf(raw);
        String[] parts = text.split("[\\r\\n,]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result.isEmpty() ? DEFAULT_STEPS : result;
    }

    private static List<String> parseStoredSteps(Object raw) {
        if (raw == null) {
            return List.of();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        String[] parts = text.split("[\\r\\n,]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<String> readConfigSteps(Object raw) {
        return readSteps(raw);
    }

    private static Timestamp completedAt(String status, Instant now) {
        return "completed".equals(status) ? Timestamp.from(now) : null;
    }

    private static Timestamp skippedAt(String status, Instant now) {
        return "skipped".equals(status) ? Timestamp.from(now) : null;
    }

    private static String iso(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static String text(Object raw, String defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? defaultValue : value;
    }
}