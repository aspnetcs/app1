package com.webchat.adminapi.auth.admin;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.adminapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/oauth")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class OAuthAdminController {

    private static final Logger log = LoggerFactory.getLogger(OAuthAdminController.class);

    private final OAuthRuntimeConfigService runtimeConfigService;

    public OAuthAdminController(OAuthRuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        OAuthRuntimeConfigService.RuntimeConfig config = runtimeConfigService.currentConfig();
        List<Map<String, Object>> providers = config.providers().entrySet().stream()
                .map(entry -> {
                    OAuthRuntimeConfigService.ProviderConfig provider = entry.getValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("provider", entry.getKey());
                    item.put("displayName", provider.displayName());
                    item.put("enabled", provider.enabled());
                    item.put("allowAdminLogin", provider.allowAdminLogin());
                    item.put("clientIdMasked", mask(provider.clientId()));
                    item.put("clientSecretConfigured", provider.clientSecret() != null && !provider.clientSecret().isBlank());
                    item.put("authorizeUri", provider.authorizeUri());
                    item.put("tokenUri", provider.tokenUri());
                    item.put("userInfoUri", provider.userInfoUri());
                    item.put("emailUri", provider.emailUri());
                    item.put("scope", provider.scope());
                    item.put("icon", provider.icon());
                    return item;
                })
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", config.enabled());
        data.put("featureKey", "platform.oauth.enabled");
        data.put("allowAdminLogin", config.allowAdminLogin());
        data.put("stateTtlSeconds", config.stateTtlSeconds());
        data.put("ticketTtlSeconds", config.ticketTtlSeconds());
        data.put("allowedRedirectHosts", config.allowedRedirectHosts());
        data.put("callbackBaseUrl", config.callbackBaseUrl());
        data.put("providers", providers);
        return ApiResponse.ok(data);
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> update(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody OAuthAdminConfigUpdateRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        try {
            runtimeConfigService.save(new OAuthRuntimeConfigService.StoredOverrides(
                    request.enabled(),
                    request.allowAdminLogin(),
                    request.providers() == null ? Map.of() : request.providers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> new OAuthRuntimeConfigService.ProviderOverride(
                                    entry.getValue() == null ? null : entry.getValue().enabled(),
                                    entry.getValue() == null ? null : entry.getValue().allowAdminLogin()
                            ),
                            (left, right) -> right,
                            LinkedHashMap::new
                    ))
            ));
            return config(userId, role);
        } catch (OAuthRuntimeConfigService.OAuthConfigException e) {
            log.warn("[oauth-admin] config update rejected for user {}: {}", userId, e.getMessage());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "oauth config update failed");
        }
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 6) return "***";
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}



