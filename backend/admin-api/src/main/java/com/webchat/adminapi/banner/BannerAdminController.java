package com.webchat.adminapi.banner;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/banners")
@ConditionalOnProperty(name = {"platform.dev-panel", "platform.banner.enabled"}, havingValue = "true", matchIfMissing = true)
public class BannerAdminController {

    private final BannerService bannerService;

    public BannerAdminController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean enabled
    ) {
        ApiResponse<?> authError = requireAdmin(userId, role);
        if (authError != null) return (ApiResponse<Map<String, Object>>) authError;
        var pageResult = bannerService.listForAdmin(search, type, enabled, page, size);
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
            return ApiResponse.ok(bannerService.create(body));
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
            return ApiResponse.ok(bannerService.update(id, body));
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
            bannerService.delete(id);
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




