package com.webchat.platformapi.history;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
public class HistorySearchController {

    private final HistorySearchService historySearchService;

    public HistorySearchController(HistorySearchService historySearchService) {
        this.historySearchService = historySearchService;
    }

    @GetMapping("/topics")
    public ApiResponse<Map<String, Object>> topics(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        String normalizedKeyword = RequestUtils.trimOrNull(keyword);
        if (normalizedKeyword == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "keyword is required");
        }
        return ApiResponse.ok(historySearchService.searchTopics(userId, normalizedKeyword, page, size));
    }

    @GetMapping("/messages")
    public ApiResponse<Map<String, Object>> messages(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam("keyword") String keyword,
            @RequestParam(name = "topicId", required = false) String topicIdText,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        String normalizedKeyword = RequestUtils.trimOrNull(keyword);
        if (normalizedKeyword == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "keyword is required");
        }

        UUID topicId;
        try {
            topicId = parseOptionalUuid(topicIdText);
        } catch (IllegalArgumentException ignored) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "topicId is invalid");
        }

        return ApiResponse.ok(historySearchService.searchMessages(userId, normalizedKeyword, topicId, page, size));
    }

    @GetMapping("/files")
    public ApiResponse<Map<String, Object>> files(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam("keyword") String keyword,
            @RequestParam(name = "topicId", required = false) String topicIdText,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        String normalizedKeyword = RequestUtils.trimOrNull(keyword);
        if (normalizedKeyword == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "keyword is required");
        }

        UUID topicId;
        try {
            topicId = parseOptionalUuid(topicIdText);
        } catch (IllegalArgumentException ignored) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "topicId is invalid");
        }

        return ApiResponse.ok(historySearchService.searchFiles(userId, normalizedKeyword, topicId, page, size));
    }

    private static UUID parseOptionalUuid(String raw) {
        String normalized = RequestUtils.trimOrNull(raw);
        if (normalized == null) {
            return null;
        }
        return UUID.fromString(normalized);
    }
}