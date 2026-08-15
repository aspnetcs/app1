package com.webchat.platformapi.knowledge;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/bases")
    public ApiResponse<List<Map<String, Object>>> listBases(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(knowledgeService.listBases(userId));
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/bases")
    public ApiResponse<Map<String, Object>> createBase(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(knowledgeService.createBase(userId, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/bases/{baseId}")
    public ApiResponse<Map<String, Object>> getBase(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                    @PathVariable UUID baseId) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(knowledgeService.getBase(userId, baseId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/bases/{baseId}/documents")
    public ApiResponse<Map<String, Object>> ingestDocument(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                           @PathVariable UUID baseId,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(knowledgeService.ingestDocument(userId, baseId, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/bases/{baseId}/jobs/{jobId}")
    public ApiResponse<Map<String, Object>> getJob(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                   @PathVariable UUID baseId,
                                                   @PathVariable UUID jobId) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(knowledgeService.getJob(userId, baseId, jobId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping("/bases/{baseId}/search")
    public ApiResponse<Map<String, Object>> search(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                   @PathVariable UUID baseId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(knowledgeService.search(userId, baseId, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    @PutMapping("/bases/{baseId}/bindings/{conversationId}")
    public ApiResponse<Map<String, Object>> bindConversation(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                             @PathVariable UUID baseId,
                                                             @PathVariable UUID conversationId,
                                                             @RequestParam(name = "enabled", defaultValue = "true") boolean enabled) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(knowledgeService.bindConversation(userId, baseId, conversationId, enabled));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @GetMapping("/bindings/{conversationId}")
    public ApiResponse<List<Map<String, Object>>> listBindings(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                               @PathVariable UUID conversationId) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        return ApiResponse.ok(knowledgeService.listBindings(userId, conversationId));
    }
}
