package com.webchat.platformapi.market;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market")
@ConditionalOnProperty(name = "platform.market.enabled", havingValue = "true", matchIfMissing = true)
public class MarketController {

    private final PlatformMarketService marketService;

    public MarketController(PlatformMarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/assets")
    public ApiResponse<List<Map<String, Object>>> listAssets(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(name = "assetType", required = false) String assetType,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        try {
            return ApiResponse.ok(marketService.listAssets(userId, assetType, keyword));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }

    @GetMapping("/saved-assets")
    public ApiResponse<List<Map<String, Object>>> listSavedAssets(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(name = "assetType", required = false) String assetType
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        try {
            return ApiResponse.ok(marketService.listSavedAssets(userId, assetType));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }

    @PostMapping("/assets/{assetType}/{sourceId}/save")
    public ApiResponse<Map<String, Object>> saveAsset(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable String assetType,
            @PathVariable String sourceId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        try {
            return ApiResponse.ok(marketService.saveAsset(userId, MarketAssetType.fromString(assetType), sourceId));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }

    @DeleteMapping("/assets/{assetType}/{sourceId}/save")
    public ApiResponse<Void> deleteSavedAsset(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable String assetType,
            @PathVariable String sourceId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        try {
            marketService.unsaveAsset(userId, MarketAssetType.fromString(assetType), sourceId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }
}
