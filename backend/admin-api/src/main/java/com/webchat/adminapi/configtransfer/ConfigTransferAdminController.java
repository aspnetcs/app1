package com.webchat.adminapi.configtransfer;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/config-transfer")
@ConditionalOnProperty(name = {"platform.dev-panel", "platform.config-transfer.enabled"}, havingValue = "true", matchIfMissing = true)
public class ConfigTransferAdminController {

    private final ConfigTransferService configTransferService;

    public ConfigTransferAdminController(ConfigTransferService configTransferService) {
        this.configTransferService = configTransferService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;
        return ApiResponse.ok(configTransferService.featureConfig());
    }

    @GetMapping("/export")
    public ApiResponse<ConfigTransferPayload> export(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(name = "modules", required = false) String modules,
            @RequestParam(name = "includeSecrets", defaultValue = "false") boolean includeSecrets
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<ConfigTransferPayload>) authErr;
        return ApiResponse.ok(configTransferService.exportPayload(splitModules(modules), includeSecrets));
    }

    @PostMapping("/preview")
    public ApiResponse<ConfigImportPreview> preview(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody ConfigTransferPayload payload
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<ConfigImportPreview>) authErr;
        try {
            return ApiResponse.ok(configTransferService.preview(payload));
        } catch (ConfigTransferService.ConfigTransferException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping("/inspect")
    public ApiResponse<Map<String, Object>> inspect(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody(required = false) ConfigTransferPayload payload
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;
        return ApiResponse.ok(configTransferService.inspect(payload));
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody ConfigTransferPayload payload
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;
        try {
            return ApiResponse.ok(configTransferService.importPayload(payload));
        } catch (ConfigTransferService.ConfigTransferException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        return null;
    }

    private static List<String> splitModules(String modules) {
        if (modules == null || modules.isBlank()) return List.of();
        return Arrays.stream(modules.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}




