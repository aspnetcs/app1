package com.webchat.adminapi.webread;

import com.webchat.platformapi.webread.*;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.webread.WebReadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/web-read")
@ConditionalOnProperty(name = {"platform.dev-panel", "platform.web-read.enabled"}, havingValue = "true", matchIfMissing = true)
public class WebReadAdminController {

    private final WebReadService webReadService;

    public WebReadAdminController(WebReadService webReadService) {
        this.webReadService = webReadService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");

        return ApiResponse.ok(Map.of(
                "enabled", webReadService.isEnabled(),
                "maxContentChars", webReadService.getMaxContentChars(),
                "connectTimeoutMs", webReadService.getConnectTimeoutMs(),
                "allowHttp", webReadService.isAllowHttp(),
                "enforceHostAllowlist", webReadService.isEnforceHostAllowlist(),
                "allowedHosts", webReadService.getAllowedHostsRaw()
        ));
    }
}




