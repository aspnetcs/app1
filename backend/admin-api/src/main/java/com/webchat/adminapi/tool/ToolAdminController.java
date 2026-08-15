package com.webchat.adminapi.tool;

import com.webchat.platformapi.ai.extension.BuiltinToolRegistry;
import com.webchat.platformapi.ai.extension.ToolRuntimeConfigService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tools")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class ToolAdminController {

    private final ToolRuntimeConfigService toolRuntimeConfigService;
    private final BuiltinToolRegistry builtinToolRegistry;

    public ToolAdminController(ToolRuntimeConfigService toolRuntimeConfigService, BuiltinToolRegistry builtinToolRegistry) {
        this.toolRuntimeConfigService = toolRuntimeConfigService;
        this.builtinToolRegistry = builtinToolRegistry;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        toolRuntimeConfigService.refresh();
        return ApiResponse.ok(builtinToolRegistry.adminConfig());
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        try {
            toolRuntimeConfigService.apply(body);
            return ApiResponse.ok(builtinToolRegistry.adminConfig());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }
}
