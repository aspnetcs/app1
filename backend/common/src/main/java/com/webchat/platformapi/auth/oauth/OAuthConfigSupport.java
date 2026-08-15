package com.webchat.platformapi.auth.oauth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OAuthConfigSupport {

    private OAuthConfigSupport() {
    }

    public static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    public static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    public static <T> Map<String, T> normalizeKeys(Map<String, T> values) {
        Map<String, T> normalized = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, T> entry : values.entrySet()) {
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        return normalized;
    }
}
