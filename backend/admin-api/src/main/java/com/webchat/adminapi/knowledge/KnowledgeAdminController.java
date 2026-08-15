package com.webchat.adminapi.knowledge;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeAdminService knowledgeAdminService;

    public KnowledgeAdminController(KnowledgeAdminService knowledgeAdminService) {
        this.knowledgeAdminService = knowledgeAdminService;
    }

    @GetMapping("/bases")
    public ApiResponse<Map<String, Object>> listBases(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                      @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role) {
        ApiResponse<?> auth = requireAdmin(userId, role);
        if (auth != null) return cast(auth);
        return ApiResponse.ok(Map.of("items", knowledgeAdminService.listBases()));
    }

    @GetMapping("/jobs")
    public ApiResponse<Map<String, Object>> listJobs(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                     @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role) {
        ApiResponse<?> auth = requireAdmin(userId, role);
        if (auth != null) return cast(auth);
        return ApiResponse.ok(Map.of("items", knowledgeAdminService.listJobs()));
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                   @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role) {
        ApiResponse<?> auth = requireAdmin(userId, role);
        if (auth != null) return cast(auth);
        return ApiResponse.ok(knowledgeAdminService.config());
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(@RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
                                                         @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        ApiResponse<?> auth = requireAdmin(userId, role);
        if (auth != null) return cast(auth);
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "config body required");
        return ApiResponse.ok(knowledgeAdminService.updateConfig(body));
    }

    @SuppressWarnings("unchecked")
    private static ApiResponse<Map<String, Object>> cast(ApiResponse<?> value) {
        return (ApiResponse<Map<String, Object>>) value;
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        return null;
    }
}
