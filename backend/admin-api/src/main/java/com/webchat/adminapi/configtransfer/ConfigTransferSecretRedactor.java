package com.webchat.adminapi.configtransfer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Redacts secret-like fields from config-transfer payloads.
 *
 * Scope: config-transfer only. This intentionally does not attempt perfect secret detection; it
 * prevents common accidental leakage when operators store secrets in free-form maps.
 */
final class ConfigTransferSecretRedactor {

    private static final String REDACTED = "***";

    // Values (not keys) that commonly contain credentials/tokens.
    private static final Pattern VALUE_OPENAI_KEY = Pattern.compile("^\\s*sk-[a-zA-Z0-9]{10,}.*$");
    private static final Pattern VALUE_BEARER = Pattern.compile("^(?i)\\s*bearer\\s+.+$");

    private ConfigTransferSecretRedactor() {
    }

    /**
     * Redact a config object (typically Map/List) recursively.
     * - Secret-like keys are removed.
     * - Secret-like string values are replaced with "***".
     */
    static Object redact(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Map<?, ?> map) {
            return redactMap(map);
        }
        if (input instanceof List<?> list) {
            return redactList(list);
        }
        if (input instanceof String s) {
            return looksLikeSecretValue(s) ? REDACTED : s;
        }
        return input;
    }

    private static Map<String, Object> redactMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
            if (key == null || key.isBlank()) {
                continue;
            }
            if (isSecretLikeKey(key)) {
                // Drop the key entirely; importers must manually re-enter secrets.
                continue;
            }
            out.put(key, (Object) redact(entry.getValue()));
        }
        return out;
    }

    private static List<Object> redactList(List<?> raw) {
        List<Object> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            out.add(redact(item));
        }
        return out;
    }

    private static boolean isSecretLikeKey(String rawKey) {
        String key = normalizeKey(rawKey);
        if (key.isEmpty()) {
            return false;
        }
        // Exact or suffix matches catch common naming variations: api_key, apiKey, x-api-key, openaiApiKey, etc.
        return key.equals("secret")
                || key.endsWith("secret")
                || key.endsWith("token")
                || key.endsWith("authorization")
                || key.endsWith("password")
                || key.endsWith("privatekey")
                || key.endsWith("apikey")
                || key.endsWith("apisecret")
                || key.endsWith("accesskey")
                || key.endsWith("secretkey")
                || key.endsWith("clientsecret")
                || key.endsWith("appsecret");
    }

    private static String normalizeKey(String rawKey) {
        if (rawKey == null) {
            return "";
        }
        String lower = rawKey.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean looksLikeSecretValue(String value) {
        if (value == null) {
            return false;
        }
        return VALUE_OPENAI_KEY.matcher(value).matches() || VALUE_BEARER.matcher(value).matches();
    }
}

