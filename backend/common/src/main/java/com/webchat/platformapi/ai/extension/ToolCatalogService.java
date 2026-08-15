package com.webchat.platformapi.ai.extension;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ToolCatalogService {

    private final ToolConfigProperties properties;
    private final BuiltinToolRegistry builtinToolRegistry;
    private final ObjectProvider<McpClientService> mcpClientServiceProvider;
    private final ToolRuntimeConfigService toolRuntimeConfigService;
    private final UserMcpToolAccessService userMcpToolAccessService;

    public ToolCatalogService(
            ToolConfigProperties properties,
            BuiltinToolRegistry builtinToolRegistry,
            ObjectProvider<McpClientService> mcpClientServiceProvider,
            ToolRuntimeConfigService toolRuntimeConfigService,
            UserMcpToolAccessService userMcpToolAccessService
    ) {
        this.properties = properties;
        this.builtinToolRegistry = builtinToolRegistry;
        this.mcpClientServiceProvider = mcpClientServiceProvider;
        this.toolRuntimeConfigService = toolRuntimeConfigService;
        this.userMcpToolAccessService = userMcpToolAccessService;
    }

    public List<ToolDefinition> listEnabledTools(List<String> requestedToolNames) {
        ToolConfigProperties current = toolRuntimeConfigService.refresh();
        if (!current.isEnabled()) {
            return List.of();
        }
        List<String> requested = ToolNameNormalizer.normalize(requestedToolNames);
        List<ToolDefinition> tools = new ArrayList<>(builtinToolRegistry.listEnabledTools(requestedToolNames));
        McpClientService mcpClientService = mcpClientServiceProvider.getIfAvailable();
        if (mcpClientService != null) {
            for (ToolDefinition definition : mcpClientService.toToolDefinitions()) {
                if (requested.isEmpty() || requested.contains(definition.name().toLowerCase(Locale.ROOT))) {
                    tools.add(definition);
                }
            }
        }
        return tools;
    }

    public Map<String, Object> userConfig() {
        return userConfig(null);
    }

    public Map<String, Object> userConfig(java.util.UUID userId) {
        ToolConfigProperties current = toolRuntimeConfigService.refresh();
        List<Map<String, Object>> tools = new ArrayList<>();
        if (current.isEnabled()) {
            for (ToolDefinition definition : builtinToolRegistry.listEnabledTools(List.of())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", definition.name());
                item.put("displayName", definition.name());
                item.put("description", definition.description());
                item.put("source", "builtin");
                tools.add(item);
            }

            McpClientService mcpClientService = mcpClientServiceProvider.getIfAvailable();
            if (mcpClientService != null) {
                tools.addAll(userId == null
                        ? mcpClientService.describeCatalog()
                        : userMcpToolAccessService.describeSelectedCatalog(userId));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", current.isEnabled());
        payload.put("featureKey", "platform.function-calling.enabled");
        payload.put("maxSteps", current.getMaxSteps());
        payload.put("tools", tools);
        return payload;
    }
}
