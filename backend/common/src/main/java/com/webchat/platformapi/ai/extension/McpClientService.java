package com.webchat.platformapi.ai.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Locale;

/**
 * MCP client service.
 * Connects to external MCP servers via HTTP/SSE, discovers available tools,
 * and maps them to ToolDefinition for use in Function Calling.
 */
@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    private final McpProperties properties;
    private final McpServerRepository repository;
    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AiCryptoService cryptoService;

    public McpClientService(
            McpProperties properties,
            ObjectProvider<McpServerRepository> repositoryProvider,
            SsrfGuard ssrfGuard,
            ObjectMapper objectMapper,
            AiCryptoService cryptoService
    ) {
        this.properties = properties;
        this.repository = repositoryProvider.getIfAvailable();
        this.ssrfGuard = ssrfGuard;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * List all tools from all enabled MCP servers.
     * Uses cached tools if available; otherwise refreshes.
     */
    public List<McpTool> listAllTools() {
        if (!properties.isEnabled() || repository == null) {
            return List.of();
        }
        List<McpServerEntity> servers = repository.findByEnabledTrue();
        List<McpTool> allTools = new ArrayList<>();

        for (McpServerEntity server : servers) {
            try {
                List<McpTool> tools = getToolsForServer(server);
                allTools.addAll(tools);
            } catch (Exception e) {
                log.warn("[mcp] Failed to get tools from server '{}': {}", server.getName(), e.getMessage());
            }
        }
        return allTools;
    }

    /**
     * Convert MCP tools to ToolDefinition for Function Calling integration.
     */
    public List<ToolDefinition> toToolDefinitions() {
        return listAllTools().stream()
                .map(t -> new ToolDefinition(
                        toolDefinitionName(t),
                        "[MCP:" + t.serverName() + "] " + t.description(),
                        t.inputSchema()
                ))
                .toList();
    }

    public List<Map<String, Object>> describeCatalog() {
        return listAllTools().stream().map(tool -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", toolDefinitionName(tool));
            item.put("displayName", tool.name());
            item.put("description", tool.description());
            item.put("serverName", tool.serverName());
            item.put("source", "mcp");
            return item;
        }).toList();
    }

    /**
     * Execute a tool on a specific MCP server.
     */
    public Map<String, Object> executeTool(Long serverId, String toolName, Map<String, Object> arguments) {
        if (repository == null) {
            throw new IllegalStateException("MCP repository is unavailable");
        }
        McpServerEntity server = repository.findById(serverId).orElseThrow(
                () -> new IllegalArgumentException("MCP server not found: " + serverId));

        try {
            ssrfGuard.assertAllowedBaseUrl(server.getEndpointUrl());
        } catch (SsrfGuard.SsrfException e) {
            throw new IllegalStateException("SSRF blocked: " + e.getMessage());
        }

        try {
            // MCP JSON-RPC call: tools/call
            Map<String, Object> rpcBody = new LinkedHashMap<>();
            rpcBody.put("jsonrpc", "2.0");
            rpcBody.put("id", UUID.randomUUID().toString());
            rpcBody.put("method", "tools/call");
            rpcBody.put("params", Map.of("name", toolName, "arguments", arguments != null ? arguments : Map.of()));

            String jsonBody = objectMapper.writeValueAsString(rpcBody);
            String url = server.getEndpointUrl().replaceAll("/+$", "");

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                    .header("Content-Type", "application/json");

            String authToken = resolveAuthToken(server);
            if (authToken != null && !authToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = reqBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MCP server error: status=" + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> rpcResult = (Map<String, Object>) result.get("result");
            return rpcResult != null ? rpcResult : Map.of("raw", result);
        } catch (Exception e) {
            log.warn("[mcp] Tool execution failed: server={}, tool={}, error={}", server.getName(), toolName, e.getMessage());
            throw new IllegalStateException("Tool execution failed: " + e.getMessage());
        }
    }

    public Map<String, Object> executeToolDefinition(String definitionName, Map<String, Object> arguments) {
        for (McpTool tool : listAllTools()) {
            if (toolDefinitionName(tool).equals(definitionName)) {
                return executeTool(tool.serverId(), tool.name(), arguments);
            }
        }
        throw new IllegalArgumentException("MCP tool not found: " + definitionName);
    }

    /**
     * Refresh tools for a specific server by calling tools/list.
     */
    public List<McpTool> refreshTools(Long serverId) {
        if (repository == null) {
            throw new IllegalStateException("MCP repository is unavailable");
        }
        McpServerEntity server = repository.findById(serverId).orElseThrow(
                () -> new IllegalArgumentException("MCP server not found: " + serverId));
        return refreshToolsForServer(server);
    }

    /**
     * Test connection to a specific MCP server.
     */
    public Map<String, Object> testConnection(Long serverId) {
        if (repository == null) {
            return Map.of("success", false, "error", "MCP repository is unavailable");
        }
        McpServerEntity server = repository.findById(serverId).orElseThrow(
                () -> new IllegalArgumentException("MCP server not found: " + serverId));

        try {
            ssrfGuard.assertAllowedBaseUrl(server.getEndpointUrl());
        } catch (SsrfGuard.SsrfException e) {
            return Map.of("success", false, "error", "SSRF blocked: " + e.getMessage());
        }

        try {
            List<McpTool> tools = refreshToolsForServer(server);
            return Map.of("success", true, "toolCount", tools.size(),
                    "tools", tools.stream().map(t -> Map.of("name", t.name(), "description", t.description())).toList());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // --- Internal helpers ---

    private List<McpTool> getToolsForServer(McpServerEntity server) {
        // Use cached tools if available
        if (server.getToolsJson() != null && !server.getToolsJson().isBlank()) {
            try {
                List<McpTool> cached = parseToolsJson(server.getToolsJson(), server.getId(), server.getName());
                if (!cached.isEmpty()) return cached;
            } catch (Exception e) {
                log.debug("[mcp] Cached tools parse failed for '{}', refreshing", server.getName());
            }
        }
        // Refresh from server
        return refreshToolsForServer(server);
    }

    private List<McpTool> refreshToolsForServer(McpServerEntity server) {
        try {
            ssrfGuard.assertAllowedBaseUrl(server.getEndpointUrl());
        } catch (SsrfGuard.SsrfException e) {
            throw new IllegalStateException("SSRF blocked: " + e.getMessage());
        }

        try {
            // MCP JSON-RPC call: tools/list
            Map<String, Object> rpcBody = new LinkedHashMap<>();
            rpcBody.put("jsonrpc", "2.0");
            rpcBody.put("id", UUID.randomUUID().toString());
            rpcBody.put("method", "tools/list");

            String jsonBody = objectMapper.writeValueAsString(rpcBody);
            String url = server.getEndpointUrl().replaceAll("/+$", "");

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                    .header("Content-Type", "application/json");

            String authToken = resolveAuthToken(server);
            if (authToken != null && !authToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = reqBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MCP tools/list error: status=" + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> rpcResult = (Map<String, Object>) result.get("result");
            String toolsJson = objectMapper.writeValueAsString(rpcResult != null ? rpcResult.get("tools") : List.of());

            // Cache the tools
            server.setToolsJson(toolsJson);
            server.setLastRefreshedAt(Instant.now());
            repository.save(server);

            return parseToolsJson(toolsJson, server.getId(), server.getName());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list tools from '" + server.getName() + "': " + e.getMessage());
        }
    }

    private List<McpTool> parseToolsJson(String toolsJson, Long serverId, String serverName) {
        try {
            List<Map<String, Object>> tools = objectMapper.readValue(toolsJson, new TypeReference<>() {});
            return tools.stream().map(t -> {
                String name = String.valueOf(t.getOrDefault("name", ""));
                String description = String.valueOf(t.getOrDefault("description", ""));
                @SuppressWarnings("unchecked")
                Map<String, Object> inputSchema = (Map<String, Object>) t.getOrDefault("inputSchema",
                        Map.of("type", "object", "properties", Map.of()));
                return new McpTool(serverId, serverName, name, description, inputSchema);
            }).toList();
        } catch (Exception e) {
            log.warn("[mcp] Failed to parse tools JSON for '{}': {}", serverName, e.getMessage());
            return List.of();
        }
    }

    public static String toolDefinitionName(McpTool tool) {
        String safeName = sanitizeToolName(tool.name());
        if (safeName.length() > 24) {
            safeName = safeName.substring(0, 24);
        }
        String hash = Integer.toHexString(Objects.hash(tool.serverId(), tool.name()));
        if (hash.startsWith("-")) {
            hash = "n" + hash.substring(1);
        }
        return "mcp_s" + tool.serverId() + "_" + safeName + "_" + hash;
    }

    private static String sanitizeToolName(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        normalized = normalized
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "tool" : normalized;
    }

    public McpServerRepository getRepository() {
        if (repository == null) {
            throw new IllegalStateException("MCP repository is unavailable");
        }
        return repository;
    }
    public McpProperties getProperties() { return properties; }

    private String resolveAuthToken(McpServerEntity server) {
        String stored = server.getAuthToken();
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (stored.startsWith("v1:")) {
            return cryptoService.decrypt(stored);
        }
        if (cryptoService.isConfigured()) {
            try {
                server.setAuthToken(cryptoService.encrypt(stored));
                repository.save(server);
                log.info("[mcp] migrated plaintext auth token for server {}", server.getId());
            } catch (Exception e) {
                log.warn("[mcp] failed to migrate plaintext auth token for server {}: {}", server.getId(), e.getMessage());
            }
        }
        return stored;
    }

    // --- DTO ---

    public record McpTool(Long serverId, String serverName, String name, String description, Map<String, Object> inputSchema) {}
}
