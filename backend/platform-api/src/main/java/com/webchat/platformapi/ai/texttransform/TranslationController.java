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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "platform.translation.enabled", havingValue = "true")
@RequestMapping("/api/v1/translation")
public class TranslationController {

    private static final Logger log = LoggerFactory.getLogger(TranslationController.class);

    private final TranslationService translationService;
    private final AuditService auditService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;

    public TranslationController(
            TranslationService translationService,
            AuditService auditService,
            UserGroupService userGroupService,
            @org.springframework.beans.factory.annotation.Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.translationService = translationService;
        this.auditService = auditService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @PostMapping("/messages")
    public ApiResponse<Map<String, Object>> translateMessage(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "translation")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "translation not allowed by group policy");
        }
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "content is required");

        String content = string(body.get("content"));
        String targetLanguage = string(body.get("targetLanguage"));
        String model = string(body.get("model"));
        TranslationService.TranslationResult result = translationService.translate(userId, model, content, targetLanguage);

        auditService.log(userId, "translation.message", Map.of(
                "targetLanguage", targetLanguage == null ? translationService.getDefaultTargetLanguage() : targetLanguage,
                "success", String.valueOf(result.success()),
                "inputChars", String.valueOf(content == null ? 0 : content.length())
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));

        if (!result.success()) {
            log.warn("[translation] translate rejected for user {}: {}", userId, result.error());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "translation service is temporarily unavailable");
        }
        return ApiResponse.ok(Map.of(
                "translatedText", result.translatedText(),
                "targetLanguage", result.targetLanguage()
        ));
    }

    private static String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
