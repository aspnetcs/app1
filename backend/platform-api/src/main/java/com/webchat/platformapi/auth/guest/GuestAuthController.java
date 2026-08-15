package com.webchat.platformapi.auth.guest;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class GuestAuthController {

    private static final Logger log = LoggerFactory.getLogger(GuestAuthController.class);

    private final GuestAuthProperties properties;
    private final GuestAuthService guestAuthService;
    private final GuestRateLimitService guestRateLimitService;

    public GuestAuthController(
            GuestAuthProperties properties,
            GuestAuthService guestAuthService,
            GuestRateLimitService guestRateLimitService
    ) {
        this.properties = properties;
        this.guestAuthService = guestAuthService;
        this.guestRateLimitService = guestRateLimitService;
    }

    @PostMapping("/guest")
    public ApiResponse<Map<String, Object>> guestLogin(
            @RequestBody(required = false) GuestLoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!properties.isEnabled()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "guest auth is disabled");
        }

        String clientIp = RequestUtils.clientIp(request);
        if (!guestRateLimitService.allowIssue(clientIp)) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "guest auth rate limit exceeded");
        }

        try {
            String deviceId = body == null ? null : body.deviceId();
            String recoveryToken = body == null ? null : body.recoveryToken();
            String deviceFingerprint = body == null ? null : body.deviceFingerprint();
            Map<String, Object> payload = guestAuthService.issueGuestSession(
                    deviceId,
                    recoveryToken,
                    deviceFingerprint,
                    clientIp,
                    RequestUtils.userAgent(request)
            );
            writeGuestRecoveryCookie(request, response, payload);
            return ApiResponse.ok("访客登录成功", payload);
        } catch (Exception e) {
            log.error("[guest-auth] issue guest session failed: ip={}", clientIp, e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "guest auth failed: " + detail);
        }
    }

    protected void writeGuestRecoveryCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            Map<String, Object> payload
    ) {
        try {
            doWriteGuestRecoveryCookie(request, response, payload);
        } catch (Exception e) {
            log.warn("[guest-auth] write recovery cookie failed", e);
        }
    }

    protected void doWriteGuestRecoveryCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            Map<String, Object> payload
    ) {
        GuestRecoveryCookieSupport.writeRecoveryCookie(
                request,
                response,
                payload.get("guestRecoveryToken") == null ? null : String.valueOf(payload.get("guestRecoveryToken")),
                properties.recoveryTokenTtl()
        );
    }
}
