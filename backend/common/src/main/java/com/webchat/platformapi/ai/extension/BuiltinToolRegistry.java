package com.webchat.platformapi.ai.extension;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BuiltinToolRegistry {

    private final ToolConfigProperties properties;

    public BuiltinToolRegistry(ToolConfigProperties properties) {
        this.properties = properties;
    }

    public List<ToolDefinition> listEnabledTools(List<String> requestedToolNames) {
        List<String> requested = ToolNameNormalizer.normalize(requestedToolNames);
        List<ToolDefinition> tools = new ArrayList<>();
        if (properties.getTools().getCurrentTime().isEnabled() && shouldInclude("current_time", requested)) {
            tools.add(new ToolDefinition(
                    "current_time",
                    "Get the current server time in Asia/Shanghai timezone.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(),
                            "required", List.of()
                    )
            ));
        }
        if (properties.getTools().getCalculator().isEnabled() && shouldInclude("calculator", requested)) {
            tools.add(new ToolDefinition(
                    "calculator",
                    "Calculate a result from two numbers and one operator.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "left", Map.of("type", "number", "description", "left operand"),
                                    "right", Map.of("type", "number", "description", "right operand"),
                                    "operator", Map.of("type", "string", "enum", List.of("+", "-", "*", "/"))
                            ),
                            "required", List.of("left", "right", "operator")
                    )
            ));
        }
        if (properties.getTools().getWebPageSummary().isEnabled() && shouldInclude("web_page_summary", requested)) {
            tools.add(new ToolDefinition(
                    "web_page_summary",
                    "Fetch a public web page and return a safe truncated summary.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "url", Map.of("type", "string", "description", "public https url")
                            ),
                            "required", List.of("url")
                    )
            ));
        }
        return tools;
    }

    public Map<String, Object> adminConfig() {
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("current_time", Map.of(
                "enabled", properties.getTools().getCurrentTime().isEnabled(),
                "description", "Get the current server time in Asia/Shanghai timezone."
        ));
        tools.put("calculator", Map.of(
                "enabled", properties.getTools().getCalculator().isEnabled(),
                "description", "Calculate a result from two numbers and one operator."
        ));
        tools.put("web_page_summary", Map.of(
                "enabled", properties.getTools().getWebPageSummary().isEnabled(),
                "description", "Fetch a public web page and return a safe truncated summary."
        ));
        return Map.of(
                "enabled", properties.isEnabled(),
                "featureKey", "platform.function-calling.enabled",
                "maxSteps", properties.getMaxSteps(),
                "tools", tools
        );
    }

    private static boolean shouldInclude(String toolName, List<String> requested) {
        return requested.isEmpty() || requested.contains(toolName);
    }
}
