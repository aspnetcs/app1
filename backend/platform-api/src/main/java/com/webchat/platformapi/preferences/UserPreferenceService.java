package com.webchat.platformapi.preferences;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserPreferenceService {

    private static final Set<String> THEME_MODES = Set.of("light", "dark", "system");
    private static final Set<String> CODE_THEMES = Set.of("system", "light", "dark");
    private static final Set<String> FONT_SCALES = Set.of("sm", "md", "lg");
    private static final Set<String> MCP_MODES = Set.of("disabled", "auto", "manual");
    private static final Pattern SPACING_PATTERN = Pattern.compile("^\\d{1,3}px$");

    private final UserPreferenceRepository repository;

    public UserPreferenceService(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> getPreferences(UUID userId) {
        UserPreferenceEntity entity = repository.findById(userId).orElseGet(() -> defaultEntity(userId));
        return toPayload(entity);
    }

    @Transactional
    public Map<String, Object> updatePreferences(UUID userId, Map<String, Object> body) {
        UserPreferenceEntity entity = repository.findById(userId).orElseGet(() -> defaultEntity(userId));
        if (body != null) {
            if (body.containsKey("defaultAgentId")) {
                entity.setDefaultAgentId(normalizeNullableString(body.get("defaultAgentId")));
            }
            if (body.containsKey("themeMode")) {
                entity.setThemeMode(normalizeEnum(body.get("themeMode"), THEME_MODES, "system"));
            }
            if (body.containsKey("codeTheme")) {
                entity.setCodeTheme(normalizeEnum(body.get("codeTheme"), CODE_THEMES, "system"));
            }
            if (body.containsKey("fontScale")) {
                entity.setFontScale(normalizeEnum(body.get("fontScale"), FONT_SCALES, "md"));
            }
            if (body.containsKey("spacingVertical")) {
                entity.setSpacingVertical(normalizeSpacing(body.get("spacingVertical"), "16px"));
            }
            if (body.containsKey("spacingHorizontal")) {
                entity.setSpacingHorizontal(normalizeSpacing(body.get("spacingHorizontal"), "16px"));
            }
            if (body.containsKey("mcpMode")) {
                entity.setMcpMode(normalizeEnum(body.get("mcpMode"), MCP_MODES, "auto"));
            }
            if (body.containsKey("preferredMcpServerId")) {
                entity.setPreferredMcpServerId(normalizeNullableString(body.get("preferredMcpServerId")));
            }
        }
        return toPayload(repository.save(entity));
    }

    private static UserPreferenceEntity defaultEntity(UUID userId) {
        UserPreferenceEntity entity = new UserPreferenceEntity();
        entity.setUserId(userId);
        return entity;
    }

    private static String normalizeNullableString(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalizeEnum(Object raw, Set<String> allowed, String fallback) {
        String value = normalizeNullableString(raw);
        if (value == null) {
            return fallback;
        }
        String normalized = value.toLowerCase();
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static String normalizeSpacing(Object raw, String fallback) {
        String value = normalizeNullableString(raw);
        if (value == null) {
            return fallback;
        }
        return SPACING_PATTERN.matcher(value).matches() ? value : fallback;
    }

    private static Map<String, Object> toPayload(UserPreferenceEntity entity) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("defaultAgentId", normalizeNullableString(entity.getDefaultAgentId()));
        payload.put("themeMode", entity.getThemeMode());
        payload.put("codeTheme", entity.getCodeTheme());
        payload.put("fontScale", entity.getFontScale());
        payload.put("spacingVertical", entity.getSpacingVertical());
        payload.put("spacingHorizontal", entity.getSpacingHorizontal());
        payload.put("mcpMode", entity.getMcpMode());
        payload.put("preferredMcpServerId", normalizeNullableString(entity.getPreferredMcpServerId()));
        return payload;
    }
}
