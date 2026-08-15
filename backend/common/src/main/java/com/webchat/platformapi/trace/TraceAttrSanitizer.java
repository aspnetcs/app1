package com.webchat.platformapi.trace;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TraceAttrSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization",
            "api-key",
            "apikey",
            "api_key",
            "x-api-key",
            "token",
            "access_token",
            "secret",
            "apiSecret",
            "api_secret",
            "password"
    );

    private TraceAttrSanitizer() {
    }

    static Map<String, Object> sanitize(Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (SENSITIVE_KEYS.contains(normalized)) {
                out.put(key, "[REDACTED]");
                continue;
            }
            Object v = e.getValue();
            if (v instanceof String s && s.length() > 2000) {
                out.put(key, s.substring(0, 2000) + "...(truncated)");
            } else {
                out.put(key, v);
            }
        }
        return out;
    }
}

