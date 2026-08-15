package com.webchat.platformapi.ai.extension;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema
) {
}
