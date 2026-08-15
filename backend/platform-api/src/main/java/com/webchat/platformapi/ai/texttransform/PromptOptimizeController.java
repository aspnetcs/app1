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
@RequestMapping("/api/v1/prompt-optimize")
public class PromptOptimizeController {

    private static final Logger log = LoggerFactory.getLogger(PromptOptimizeController.class);

    private final PromptOptimizeService promptOptimizeService;
    private final AuditService auditService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;

    public PromptOptimizeController(
            PromptOptimizeService promptOptimizeService,
            AuditService auditService,
            UserGroupService userGroupService,
            @org.springframework.beans.factory.annotation.Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.promptOptimizeService = promptOptimizeService;
        this.auditService = auditService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> optimize(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "content is required");
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "prompt_optimize")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "prompt optimize not allowed by group policy");
        }

        String content = string(body.get("content"));
        String direction = string(body.get("direction"));
        String model = string(body.get("model"));
        PromptOptimizeService.OptimizeResult result = promptOptimizeService.optimize(userId, model, content, direction);

        auditService.log(userId, "prompt.optimize", Map.of(
                "direction", direction == null ? promptOptimizeService.getDefaultDirection() : direction,
                "success", String.valueOf(result.success()),
                "inputChars", String.valueOf(content == null ? 0 : content.length())
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));

        if (!result.success()) {
            log.warn("[prompt-optimize] optimize rejected for user {}: {}", userId, result.error());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "prompt optimize service is temporarily unavailable");
        }
        return ApiResponse.ok(Map.of(
                "optimizedPrompt", result.optimizedPrompt(),
                "direction", result.direction()
        ));
    }

    private static String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
