package com.webchat.platformapi.ai.texttransform;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.auth.group.UserGroupService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/follow-up")
public class FollowUpSuggestionController {

    private static final Logger log = LoggerFactory.getLogger(FollowUpSuggestionController.class);

    private final FollowUpSuggestionService service;
    private final AuditService auditService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;

    public FollowUpSuggestionController(
            FollowUpSuggestionService service,
            AuditService auditService,
            UserGroupService userGroupService,
            @org.springframework.beans.factory.annotation.Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.service = service;
        this.auditService = auditService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @PostMapping("/suggestions")
    public ApiResponse<Map<String, Object>> suggest(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "context is required");
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "follow_up")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "follow-up not allowed by group policy");
        }

        String context = string(body.get("context"));
        String model = string(body.get("model"));
        FollowUpSuggestionService.SuggestionResult result = service.suggest(userId, model, context);

        auditService.log(userId, "followup.suggest", Map.of(
                "success", String.valueOf(result.success()),
                "contextChars", String.valueOf(context == null ? 0 : context.length()),
                "count", String.valueOf(result.suggestions().size())
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));

        if (!result.success()) {
            log.warn("[follow-up] suggest rejected for user {}: {}", userId, result.error());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "follow-up service is temporarily unavailable");
        }
        return ApiResponse.ok(Map.of("suggestions", result.suggestions()));
    }

    private static String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
