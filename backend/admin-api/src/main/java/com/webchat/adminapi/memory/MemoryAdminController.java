package com.webchat.adminapi.memory;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/memory")
public class MemoryAdminController {

    private final MemoryAdminService memoryAdminService;

    public MemoryAdminController(MemoryAdminService memoryAdminService) {
        this.memoryAdminService = memoryAdminService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return ApiResponse.ok(memoryAdminService.getConfig());
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        if (body == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "config body required");
        }
        return ApiResponse.ok(memoryAdminService.updateConfig(body));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return ApiResponse.ok(memoryAdminService.getStats());
    }

    @GetMapping("/audits")
    public ApiResponse<Map<String, Object>> audits(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return ApiResponse.ok(memoryAdminService.getAudits(page, size));
    }

    private static ApiResponse<Map<String, Object>> requireAdmin(UUID userId, String role) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }
        return null;
    }
}
