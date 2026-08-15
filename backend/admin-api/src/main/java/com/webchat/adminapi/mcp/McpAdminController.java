package com.webchat.adminapi.mcp;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.ai.extension.McpClientService;
import com.webchat.platformapi.ai.extension.McpProperties;
import com.webchat.platformapi.ai.extension.McpServerEntity;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.config.SysConfigService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/mcp")
@ConditionalOnProperty(name = {"platform.dev-panel", "platform.mcp.enabled"}, havingValue = "true", matchIfMissing = true)
public class McpAdminController {

    private static final String SUPPORTED_TRANSPORT = "http_sse";

    private static final String CFG_MCP_ENABLED = "mcp.enabled";
    private static final String CFG_MCP_MAX_SERVERS = "mcp.max_servers";
    private static final String CFG_MCP_TIMEOUT = "mcp.request_timeout_ms";

    private final McpClientService mcpClientService;
    private final AiCryptoService cryptoService;
    private final SysConfigService sysConfigService;

    public McpAdminController(McpClientService mcpClientService, AiCryptoService cryptoService, SysConfigService sysConfigService) {
        this.mcpClientService = mcpClientService;
        this.cryptoService = cryptoService;
        this.sysConfigService = sysConfigService;
        loadPersistedConfig();
    }

    private void loadPersistedConfig() {
        McpProperties props = mcpClientService.getProperties();
        sysConfigService.get(CFG_MCP_ENABLED).ifPresent(v -> props.setEnabled(Boolean.parseBoolean(v)));
        sysConfigService.get(CFG_MCP_MAX_SERVERS).ifPresent(v -> { try { props.setMaxServers(Integer.parseInt(v)); } catch (NumberFormatException ignored) {} });
        sysConfigService.get(CFG_MCP_TIMEOUT).ifPresent(v -> { try { props.setRequestTimeoutMs(Long.parseLong(v)); } catch (NumberFormatException ignored) {} });
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        McpProperties props = mcpClientService.getProperties();
        return ApiResponse.ok(Map.of(
                "enabled", props.isEnabled(),
                "maxServers", props.getMaxServers(),
                "requestTimeoutMs", props.getRequestTimeoutMs()
        ));
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        McpProperties props = mcpClientService.getProperties();
        if (body.containsKey("enabled")) {
            boolean val = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            props.setEnabled(val);
            sysConfigService.set(CFG_MCP_ENABLED, String.valueOf(val));
        }
        if (body.containsKey("maxServers")) {
            Integer val = parseInt(body.get("maxServers"));
            if (val == null || val < 1) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "maxServers must be >= 1");
            }
            props.setMaxServers(val);
            sysConfigService.set(CFG_MCP_MAX_SERVERS, String.valueOf(val));
        }
        if (body.containsKey("requestTimeoutMs")) {
            Long val = parseLong(body.get("requestTimeoutMs"));
            if (val == null || val < 1000) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "requestTimeoutMs must be >= 1000");
            }
            props.setRequestTimeoutMs(val);
            sysConfigService.set(CFG_MCP_TIMEOUT, String.valueOf(val));
        }
        return ApiResponse.ok(Map.of(
                "enabled", props.isEnabled(),
                "maxServers", props.getMaxServers(),
                "requestTimeoutMs", props.getRequestTimeoutMs()
        ));
    }

    @GetMapping("/servers")
    public ApiResponse<List<Map<String, Object>>> listServers(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        List<Map<String, Object>> payload = mcpClientService.getRepository().findAll().stream().map(server -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", server.getId());
            map.put("name", server.getName());
            map.put("description", server.getDescription());
            map.put("endpointUrl", server.getEndpointUrl());
            map.put("transportType", server.getTransportType());
            map.put("enabled", server.isEnabled());
            map.put("lastRefreshedAt", server.getLastRefreshedAt());
            map.put("createdAt", server.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.ok(payload);
    }

    @PostMapping("/servers")
    public ApiResponse<Map<String, Object>> createServer(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        if (mcpClientService.getRepository().count() >= mcpClientService.getProperties().getMaxServers()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "max MCP servers reached");
        }
        McpServerEntity entity = new McpServerEntity();
        entity.setName(String.valueOf(body.getOrDefault("name", "")).trim());
        entity.setDescription(String.valueOf(body.getOrDefault("description", "")));
        entity.setEndpointUrl(String.valueOf(body.getOrDefault("endpointUrl", "")).trim());
        if (entity.getName().isBlank() || entity.getEndpointUrl().isBlank()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "name and endpointUrl are required");
        }
        String transportType = normalizeTransportType(body.get("transportType"));
        if (transportType == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "only http_sse transport is supported");
        }
        entity.setTransportType(transportType);
        try {
            entity.setAuthToken(encryptAuthToken(body.get("authToken")));
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "mcp token encryption is unavailable");
        }
        entity.setEnabled(Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", "true"))));
        entity = mcpClientService.getRepository().save(entity);
        return ApiResponse.ok(Map.of("id", entity.getId(), "name", entity.getName()));
    }

    @PutMapping("/servers/{id}")
    public ApiResponse<Map<String, Object>> updateServer(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        McpServerEntity entity = mcpClientService.getRepository().findById(id).orElse(null);
        if (entity == null) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "MCP server not found");
        if (body.containsKey("name")) entity.setName(String.valueOf(body.get("name")).trim());
        if (body.containsKey("description")) entity.setDescription(String.valueOf(body.get("description")));
        if (body.containsKey("endpointUrl")) entity.setEndpointUrl(String.valueOf(body.get("endpointUrl")).trim());
        if (entity.getName() == null || entity.getName().isBlank() || entity.getEndpointUrl() == null || entity.getEndpointUrl().isBlank()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "name and endpointUrl are required");
        }
        if (body.containsKey("transportType")) {
            String transportType = normalizeTransportType(body.get("transportType"));
            if (transportType == null) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "only http_sse transport is supported");
            }
            entity.setTransportType(transportType);
        }
        if (body.containsKey("authToken")) {
            try {
                entity.setAuthToken(encryptAuthToken(body.get("authToken")));
            } catch (IllegalStateException e) {
                return ApiResponse.error(ErrorCodes.SERVER_ERROR, "mcp token encryption is unavailable");
            }
        }
        if (body.containsKey("enabled")) entity.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        entity = mcpClientService.getRepository().save(entity);
        return ApiResponse.ok(Map.of("id", entity.getId(), "name", entity.getName()));
    }

    @DeleteMapping("/servers/{id}")
    public ApiResponse<Void> deleteServer(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable Long id
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        mcpClientService.getRepository().deleteById(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/servers/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable Long id
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        if (!mcpClientService.getProperties().isEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "mcp is disabled");
        }
        Optional<McpServerEntity> server = mcpClientService.getRepository().findById(id);
        if (server.isEmpty()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "MCP server not found");
        }
        if (!server.get().isEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "MCP server is disabled");
        }
        return ApiResponse.ok(mcpClientService.testConnection(id));
    }

    @PostMapping("/servers/{id}/refresh")
    public ApiResponse<List<Map<String, Object>>> refreshTools(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable Long id
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        if (!mcpClientService.getProperties().isEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "mcp is disabled");
        }
        Optional<McpServerEntity> server = mcpClientService.getRepository().findById(id);
        if (server.isEmpty()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "MCP server not found");
        }
        if (!server.get().isEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "MCP server is disabled");
        }
        List<Map<String, Object>> payload = mcpClientService.refreshTools(id).stream().map(tool -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", tool.name());
            map.put("description", tool.description());
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.ok(payload);
    }

    private static String normalizeTransportType(Object raw) {
        String value = raw == null ? SUPPORTED_TRANSPORT : String.valueOf(raw).trim().toLowerCase();
        if (SUPPORTED_TRANSPORT.equals(value)) {
            return value;
        }
        return null;
    }

    private static Integer parseInt(Object raw) {
        if (raw instanceof Number number) return number.intValue();
        if (raw == null) return null;
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLong(Object raw) {
        if (raw instanceof Number number) return number.longValue();
        if (raw == null) return null;
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String encryptAuthToken(Object raw) {
        if (raw == null) {
            return null;
        }
        String token = String.valueOf(raw).trim();
        if (token.isEmpty()) {
            return null;
        }
        if (!cryptoService.isConfigured()) {
            throw new IllegalStateException("MCP token encryption is not configured");
        }
        return cryptoService.encrypt(token);
    }
}




