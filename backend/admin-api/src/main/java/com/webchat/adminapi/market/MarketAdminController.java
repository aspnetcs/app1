package com.webchat.adminapi.market;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/market")
@ConditionalOnProperty(name = "platform.market.enabled", havingValue = "true", matchIfMissing = true)
public class MarketAdminController {

    private final MarketAdminService marketAdminService;

    public MarketAdminController(MarketAdminService marketAdminService) {
        this.marketAdminService = marketAdminService;
    }

    @GetMapping("/catalog")
    public ApiResponse<Map<String, Object>> listCatalog(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(name = "assetType", required = false) String assetType,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        if (!isAdmin(userId, role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        try {
            return ApiResponse.ok(marketAdminService.listCatalog(assetType, keyword));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }

    @PostMapping("/catalog")
    public ApiResponse<Map<String, Object>> createCatalogItem(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (!isAdmin(userId, role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        try {
            return ApiResponse.ok(marketAdminService.createCatalogItem(body));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }

    @PutMapping("/catalog/{id}")
    public ApiResponse<Map<String, Object>> updateCatalogItem(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (!isAdmin(userId, role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        try {
            return ApiResponse.ok(marketAdminService.updateCatalogItem(id, body));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }

    @GetMapping("/source-options")
    public ApiResponse<List<Map<String, Object>>> listSourceOptions(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(name = "assetType", required = false) String assetType
    ) {
        if (!isAdmin(userId, role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        try {
            return ApiResponse.ok(marketAdminService.listSourceOptions(assetType));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }

    private boolean isAdmin(UUID userId, String role) {
        return userId != null && "admin".equalsIgnoreCase(role);
    }
}
