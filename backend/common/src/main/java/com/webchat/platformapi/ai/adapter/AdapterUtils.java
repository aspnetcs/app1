package com.webchat.platformapi.ai.adapter;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared adapter utilities — eliminates duplication across AnthropicAdapter, GeminiAdapter, etc.
 */
public final class AdapterUtils {
    private AdapterUtils() {}

    /**
     * Normalize base URL: strip trailing slash.
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        String s = baseUrl.trim();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Safe int extraction from JsonNode.
     */
    public static int asInt(JsonNode node, String field, int defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return defaultValue;
        if (v.isNumber()) return v.intValue();
        try { return Integer.parseInt(v.asText()); } catch (Exception e) { return defaultValue; }
    }

    /**
     * Safe double extraction from JsonNode.
     */
    public static double asDouble(JsonNode node, String field, double defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return defaultValue;
        if (v.isNumber()) return v.doubleValue();
        try { return Double.parseDouble(v.asText()); } catch (Exception e) { return defaultValue; }
    }

    /**
     * Safe string extraction from JsonNode.
     */
    public static String string(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /**
     * Extract text content from various chat completion response formats.
     * Works for both OpenAI-style and custom formats.
     */
    public static String extractTextContent(JsonNode choices) {
        if (choices == null || !choices.isArray() || choices.isEmpty()) return null;
        JsonNode first = choices.get(0);
        if (first == null) return null;

        // OpenAI format: choices[0].delta.content or choices[0].message.content
        for (String wrapper : new String[]{"delta", "message"}) {
            JsonNode w = first.get(wrapper);
            if (w != null && w.has("content")) {
                JsonNode c = w.get("content");
                if (c != null && !c.isNull() && c.isTextual()) return c.asText();
            }
        }

        // Direct content field
        if (first.has("content")) {
            JsonNode c = first.get("content");
            if (c != null && !c.isNull() && c.isTextual()) return c.asText();
        }

        return null;
    }
}
