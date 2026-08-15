package com.webchat.platformapi.feature.webread;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.webread.WebReadService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "platform.web-read.enabled", havingValue = "true")
@RequestMapping("/api/v1/web-read")
public class WebReadController {

    private static final Logger log = LoggerFactory.getLogger(WebReadController.class);

    private final WebReadService webReadService;
    private final AuditService auditService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;

    public WebReadController(
            WebReadService webReadService,
            AuditService auditService,
            UserGroupService userGroupService,
            @org.springframework.beans.factory.annotation.Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.webReadService = webReadService;
        this.auditService = auditService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @PostMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "web_read")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "web read not allowed by group policy");
        }
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "url is required");
        String url = string(body.get("url"));
        WebReadService.WebReadResult result = webReadService.read(url);

        auditService.log(userId, "webread.fetch", Map.of(
                "success", String.valueOf(result.success()),
                "url", result.url() == null ? "" : result.url(),
                "contentLength", String.valueOf(result.contentLength()),
                "truncated", String.valueOf(result.truncated())
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));

        if (!result.success()) {
            log.warn("[webread] fetch rejected for user {}: {}", userId, result.error());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "web read is temporarily unavailable");
        }
        return ApiResponse.ok(Map.of(
                "url", result.url(),
                "title", result.title(),
                "content", result.content(),
                "truncated", result.truncated(),
                "contentLength", result.contentLength()
        ));
    }

    private static String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
