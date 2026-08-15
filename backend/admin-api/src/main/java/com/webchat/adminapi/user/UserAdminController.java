package com.webchat.adminapi.user;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Autowired
    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String roleFilter
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) {
            return (ApiResponse<Map<String, Object>>) authErr;
        }
        return userAdminService.list(page, size, search, roleFilter);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("id") UUID id
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) {
            return (ApiResponse<Map<String, Object>>) authErr;
        }
        return userAdminService.detail(id);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> update(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) {
            return (ApiResponse<Map<String, Object>>) authErr;
        }
        return userAdminService.update(id, body);
    }

    @PostMapping("/{id}/reset-token-usage")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> resetTokenUsage(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("id") UUID id
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) {
            return (ApiResponse<Map<String, Object>>) authErr;
        }
        return userAdminService.resetTokenUsage(id);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) {
            return (ApiResponse<Map<String, Object>>) authErr;
        }
        return userAdminService.stats();
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足");
        }
        return null;
    }
}
