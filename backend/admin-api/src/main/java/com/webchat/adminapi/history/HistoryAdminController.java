package com.webchat.adminapi.history;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/history")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class HistoryAdminController {

    private final HistoryAdminService historyAdminService;

    public HistoryAdminController(HistoryAdminService historyAdminService) {
        this.historyAdminService = historyAdminService;
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam("keyword") String keyword,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "userId", required = false) String targetUserIdText,
            @RequestParam(name = "topicId", required = false) String topicIdText,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }

        String normalizedKeyword = RequestUtils.trimOrNull(keyword);
        if (normalizedKeyword == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "keyword is required");
        }

        UUID targetUserId;
        UUID topicId;
        try {
            targetUserId = parseOptionalUuid(targetUserIdText);
        } catch (IllegalArgumentException ignored) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "userId is invalid");
        }
        try {
            topicId = parseOptionalUuid(topicIdText);
        } catch (IllegalArgumentException ignored) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "topicId is invalid");
        }

        try {
            return ApiResponse.ok(historyAdminService.search(type, normalizedKeyword, targetUserId, topicId, page, size));
        } catch (IllegalArgumentException ignored) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "type is invalid");
        }
    }

    private static UUID parseOptionalUuid(String raw) {
        String normalized = RequestUtils.trimOrNull(raw);
        if (normalized == null) {
            return null;
        }
        return UUID.fromString(normalized);
    }
}