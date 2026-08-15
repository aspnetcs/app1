package com.webchat.adminapi.feature;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

record FeatureField(
        String key,
        String propertyKey,
        FeatureFieldType type,
        Object defaultValue,
        Integer min,
        Integer max,
        boolean readOnly
) {

    static FeatureField bool(String key, String propertyKey, boolean defaultValue) {
        return new FeatureField(key, propertyKey, FeatureFieldType.BOOLEAN, defaultValue, null, null, false);
    }

    static FeatureField text(String key, String propertyKey, String defaultValue) {
        return new FeatureField(key, propertyKey, FeatureFieldType.TEXT, defaultValue, null, null, false);
    }

    static FeatureField text(String key, String propertyKey, String defaultValue, boolean readOnly) {
        return new FeatureField(key, propertyKey, FeatureFieldType.TEXT, defaultValue, null, null, readOnly);
    }

    static FeatureField number(String key, String propertyKey, int defaultValue, Integer min, Integer max) {
        return new FeatureField(key, propertyKey, FeatureFieldType.NUMBER, defaultValue, min, max, false);
    }

    static FeatureField list(String key, String propertyKey, List<String> defaultValue) {
        return new FeatureField(key, propertyKey, FeatureFieldType.LIST, List.copyOf(defaultValue), null, null, false);
    }

    static FeatureField list(String key, String propertyKey, List<String> defaultValue, boolean readOnly) {
        return new FeatureField(key, propertyKey, FeatureFieldType.LIST, List.copyOf(defaultValue), null, null, readOnly);
    }

    Object readValue(Environment environment, SysConfigService sysConfigService) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return defaultValue;
        }
        Optional<String> persisted = sysConfigService.get(propertyKey);
        String raw = persisted.orElseGet(() -> environment.getProperty(propertyKey));
        return parse(raw);
    }

    String serialize(Object rawValue) {
        return switch (type) {
            case BOOLEAN -> String.valueOf(parseBoolean(rawValue));
            case NUMBER -> String.valueOf(parseNumber(rawValue));
            case TEXT -> parseText(rawValue);
            case LIST -> String.join(",", parseList(rawValue));
        };
    }

    private Object parse(String raw) {
        if (raw == null) {
            return defaultValue;
        }
        return switch (type) {
            case BOOLEAN -> parseBoolean(raw);
            case NUMBER -> parseNumber(raw);
            case TEXT -> raw;
            case LIST -> parseList(raw);
        };
    }

    private boolean parseBoolean(Object raw) {
        if (raw == null) {
            return Boolean.TRUE.equals(defaultValue);
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(raw).trim());
    }

    private int parseNumber(Object raw) {
        int fallback = defaultValue instanceof Number number ? number.intValue() : 0;
        if (raw == null) {
            return fallback;
        }
        int parsed;
        if (raw instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(raw).trim());
            } catch (Exception e) {
                throw new IllegalArgumentException(key + " must be a number");
            }
        }
        if (min != null && parsed < min) {
            throw new IllegalArgumentException(key + " must be >= " + min);
        }
        if (max != null && parsed > max) {
            throw new IllegalArgumentException(key + " must be <= " + max);
        }
        return parsed;
    }

    private String parseText(Object raw) {
        if (raw == null) {
            return defaultValue == null ? "" : String.valueOf(defaultValue);
        }
        return String.valueOf(raw).trim();
    }

    private List<String> parseList(Object raw) {
        List<String> defaults = defaultValue instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        if (raw == null) {
            return defaults;
        }
        if (raw instanceof List<?> list) {
            return sanitizeList(list);
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        String[] segments = text.split(",");
        List<String> result = new ArrayList<>();
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> sanitizeList(List<?> list) {
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = item == null ? "" : String.valueOf(item).trim();
            if (!text.isEmpty() && !result.contains(text)) {
                result.add(text);
            }
        }
        return result;
    }
}
