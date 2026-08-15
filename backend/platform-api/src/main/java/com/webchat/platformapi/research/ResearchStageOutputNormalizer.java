package com.webchat.platformapi.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Normalizes persisted research stage output so every completed stage
 * has a stable displayable content field for user-facing rendering.
 */
public class ResearchStageOutputNormalizer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String normalizeStoredOutputJson(String storedOutputJson) {
        if (storedOutputJson == null || storedOutputJson.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(storedOutputJson);
            return normalizeParsedNode(node, storedOutputJson);
        } catch (Exception ignored) {
            return wrapPlainText(storedOutputJson);
        }
    }

    public String extractDisplayContent(String storedOutputJson) {
        String normalized = normalizeStoredOutputJson(storedOutputJson);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(normalized);
            JsonNode contentNode = node.get("content");
            if (contentNode != null && contentNode.isTextual()) {
                String content = contentNode.asText();
                return content == null || content.isBlank() ? null : content;
            }
        } catch (Exception ignored) {
            return normalized;
        }
        return normalized;
    }

    private String normalizeParsedNode(JsonNode node, String rawText) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return wrapPlainText(node.asText(""));
        }
        if (node.isObject()) {
            ObjectNode normalized = ((ObjectNode) node).deepCopy();
            stripBlankTextFields(normalized);
            String content = firstNonBlankText(
                    normalized,
                    "content",
                    "document",
                    "assessment",
                    "summary",
                    "result",
                    "text",
                    "output"
            );
            if (content == null) {
                if (!hasMeaningfulObjectPayload(normalized)) {
                    return null;
                }
                content = prettyJson(normalized);
            }
            if (!normalized.hasNonNull("format") || normalized.path("format").asText("").isBlank()) {
                normalized.put("format", guessOutputFormat(content));
            }
            if (!normalized.hasNonNull("content") || normalized.path("content").asText("").isBlank()) {
                normalized.put("content", content);
            }
            return toJson(normalized);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        String content = prettyJson(node);
        payload.put("format", guessOutputFormat(content));
        payload.put("content", content);
        payload.set("raw", node);
        return toJson(payload);
    }

    private String wrapPlainText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("format", guessOutputFormat(text));
        payload.put("content", text);
        return toJson(payload);
    }

    private String firstNonBlankText(ObjectNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isTextual()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private void stripBlankTextFields(ObjectNode node) {
        if (node == null) {
            return;
        }
        java.util.List<String> blankFields = new java.util.ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual() && value.asText().isBlank()) {
                blankFields.add(entry.getKey());
            }
        });
        blankFields.forEach(node::remove);
    }

    private boolean hasMeaningfulObjectPayload(ObjectNode node) {
        if (node == null) {
            return false;
        }
        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            java.util.Map.Entry<String, JsonNode> entry = fields.next();
            if ("format".equals(entry.getKey())) {
                continue;
            }
            if (isMeaningfulValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMeaningfulValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText().isBlank();
        }
        if (value.isObject()) {
            java.util.Iterator<JsonNode> children = value.elements();
            while (children.hasNext()) {
                if (isMeaningfulValue(children.next())) {
                    return true;
                }
            }
            return false;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (isMeaningfulValue(item)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private String prettyJson(JsonNode value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private String toJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return value.toString();
        }
    }

    private String guessOutputFormat(String text) {
        if (text == null || text.isBlank()) {
            return "text";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("#") || trimmed.contains("\n#") || trimmed.contains("```")) {
            return "markdown";
        }
        return "text";
    }
}
