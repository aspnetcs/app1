package com.webchat.adminapi.group;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/groups")
@ConditionalOnProperty(name = {"platform.dev-panel", "platform.user-groups.enabled"}, havingValue = "true", matchIfMissing = true)
public class UserGroupAdminController {

    private final UserGroupAdminService userGroupService;

    public UserGroupAdminController(UserGroupAdminService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Map<String, Object>>) authError;
        var pageResult = userGroupService.listForAdmin(search, enabled, page, size);
        return ApiResponse.ok(Map.of(
                "items", pageResult.getContent(),
                "total", pageResult.getTotalElements(),
                "page", pageResult.getNumber(),
                "size", pageResult.getSize()
        ));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Map<String, Object>>) authError;
        try {
            return ApiResponse.ok(userGroupService.create(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Map<String, Object>>) authError;
        try {
            return ApiResponse.ok(userGroupService.update(id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable UUID id
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Void>) authError;
        try {
            userGroupService.delete(id);
            return ApiResponse.ok("ok", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        return null;
    }
}




