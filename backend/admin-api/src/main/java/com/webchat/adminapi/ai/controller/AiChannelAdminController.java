package com.webchat.adminapi.ai.controller;

import com.webchat.adminapi.ai.dto.ChannelFetchModelsRequest;
import com.webchat.adminapi.ai.dto.ChannelKeyStatusUpdateRequest;
import com.webchat.adminapi.ai.dto.ChannelStatusUpdateRequest;
import com.webchat.adminapi.ai.dto.ChannelTestRequest;
import com.webchat.adminapi.ai.helper.ChannelValidationHelper;
import com.webchat.adminapi.ai.service.AiChannelAdminService;
import com.webchat.platformapi.ai.channel.AiChannelKeyRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.controller.dto.ChannelKeysRequest;
import com.webchat.platformapi.ai.controller.dto.ChannelUpsertRequest;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Dev-only admin endpoints for managing AI channels/keys.
 * Production should add proper RBAC; this is gated by platform.dev-panel=true.
 */
@RestController
@RequestMapping("/api/v1/admin/channels")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class AiChannelAdminController {

    private final AiChannelAdminService aiChannelAdminService;

    @Autowired
    public AiChannelAdminController(AiChannelAdminService aiChannelAdminService) {
        this.aiChannelAdminService = aiChannelAdminService;
    }

    // Kept for standalone unit tests that build controller directly with repositories.
    public AiChannelAdminController(
            AiChannelRepository channelRepo,
            AiChannelKeyRepository keyRepo,
            AiCryptoService crypto,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            JdbcTemplate jdbc
    ) {
        this(new AiChannelAdminService(
                channelRepo,
                keyRepo,
                crypto,
                new ChannelValidationHelper(ssrfGuard),
                channelMonitor,
                jdbc
        ));
    }

    @GetMapping
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> list(
            HttpServletRequest request,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.list(page, size, keyword);
    }

    @PostMapping
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody ChannelUpsertRequest req) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.create(req);
    }

    @PutMapping("/{id}")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> update(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestBody ChannelUpsertRequest req
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable("id") Long id) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.delete(id);
    }

    @PostMapping("/fetch-models")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> fetchModels(HttpServletRequest request, @RequestBody ChannelFetchModelsRequest body) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.fetchModels(body);
    }

    @PutMapping("/{id}/status")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> updateStatus(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestBody ChannelStatusUpdateRequest body
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.updateStatus(id, body);
    }

    @PostMapping("/{id}/test")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<ChannelMonitor.ProbeResult> test(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestBody(required = false) ChannelTestRequest body
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.test(id, body);
    }

    @PostMapping("/test-all")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> testAll(
            HttpServletRequest request,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestBody(required = false) ChannelTestRequest body
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.testAll(limit, body);
    }

    @GetMapping("/usage")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> usage(
            HttpServletRequest request,
            @RequestParam(name = "hours", required = false) Integer hours
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.usage(hours);
    }

    @GetMapping("/{id}/keys")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<List<Map<String, Object>>> listKeys(HttpServletRequest request, @PathVariable("id") Long id) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.listKeys(id);
    }

    @PostMapping("/{id}/keys")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Map<String, Object>> addKeys(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestBody ChannelKeysRequest req
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.addKeys(id, req);
    }

    @PutMapping("/{id}/keys/{keyId}/status")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Void> updateKeyStatus(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @PathVariable("keyId") Long keyId,
            @RequestBody ChannelKeyStatusUpdateRequest body
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.updateKeyStatus(id, keyId, body);
    }

    @DeleteMapping("/{id}/keys/{keyId}")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ApiResponse<Void> deleteKey(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @PathVariable("keyId") Long keyId
    ) {
        ApiResponse<?> authErr = requireAuth(request);
        if (authErr != null) {
            return (ApiResponse) authErr;
        }
        return aiChannelAdminService.deleteKey(id, keyId);
    }

    @SuppressWarnings("rawtypes")
    private static ApiResponse requireAuth(HttpServletRequest request) {
        Object userId = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        Object roleObj = request.getAttribute(JwtAuthFilter.ATTR_USER_ROLE);
        String role = roleObj != null ? String.valueOf(roleObj).trim() : "";
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足，需要管理员角色");
        }
        return null;
    }
}
