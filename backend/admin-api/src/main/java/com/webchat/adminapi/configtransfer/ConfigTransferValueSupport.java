package com.webchat.adminapi.configtransfer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ConfigTransferValueSupport {

    private ConfigTransferValueSupport() {
    }

    static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    static UUID uuid(Object value) {
        try {
            String text = value == null ? null : String.valueOf(value).trim();
            return text == null || text.isEmpty() ? null : UUID.fromString(text);
        } catch (Exception e) {
            return null;
        }
    }

    static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    static int intValue(Object value, int fallback) {
        Integer parsed = integer(value);
        return parsed == null ? fallback : parsed;
    }

    static Boolean boolOrNull(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    static boolean bool(Object value, boolean fallback) {
        Boolean parsed = boolOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    static Map<String, String> mapOfStrings(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = text(entry.getKey());
            String itemValue = text(entry.getValue());
            if (key != null && itemValue != null) {
                output.put(key, itemValue);
            }
        }
        return output;
    }

    static Map<String, Object> mapOfObjects(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = text(entry.getKey());
            if (key != null) {
                output.put(key, entry.getValue());
            }
        }
        return output;
    }

    static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> output = new ArrayList<>();
        for (String item : value.split(",")) {
            String normalized = item.trim();
            if (!normalized.isEmpty()) {
                output.add(normalized);
            }
        }
        return output;
    }
}
