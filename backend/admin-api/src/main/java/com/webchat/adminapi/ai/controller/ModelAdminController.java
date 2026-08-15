package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.channel.NoChannelException;
import com.webchat.platformapi.ai.model.AiModelCapabilityResolver;
import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import com.webchat.platformapi.ai.model.AiModelMetadataService;
import com.webchat.platformapi.ai.model.ModelMetadataRequest;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/models")
@ConditionalOnProperty(name = {"platform.dev-panel", "platform.model-metadata.enabled"}, havingValue = "true", matchIfMissing = true)
public class ModelAdminController {

    private final AiModelMetadataService metadataService;
    private final ChannelRouter channelRouter;
    private final ChannelMonitor channelMonitor;
    private final AiModelCapabilityResolver capabilityResolver;

    public ModelAdminController(
            AiModelMetadataService metadataService,
            ChannelRouter channelRouter,
            ChannelMonitor channelMonitor,
            AiModelCapabilityResolver capabilityResolver
    ) {
        this.metadataService = metadataService;
        this.channelRouter = channelRouter;
        this.channelMonitor = channelMonitor;
        this.capabilityResolver = capabilityResolver;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String keyword
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        int safePage = Math.max(0, page);
        if (size <= 0) {
            List<Map<String, Object>> items = metadataService.listCatalogAll(RequestUtils.trimOrNull(keyword));
            return ApiResponse.ok(Map.of(
                    "items", items,
                    "total", items.size(),
                    "page", 0,
                    "size", items.size()
            ));
        }
        int safeSize = Math.max(1, Math.min(2000, size));
        var entries = metadataService.listCatalog(PageRequest.of(safePage, safeSize), RequestUtils.trimOrNull(keyword));
        List<Map<String, Object>> items = entries.getContent();
        return ApiResponse.ok(Map.of(
                "items", items,
                "total", entries.getTotalElements(),
                "page", entries.getNumber(),
                "size", entries.getSize()
        ));
    }

    @PutMapping("/{modelId}")
    public ApiResponse<Map<String, Object>> update(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable String modelId,
            @RequestBody ModelMetadataRequest request
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        try {
            AiModelMetadataEntity updated = metadataService.upsert(modelId, request);
            return ApiResponse.ok(toMap(updated));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @PostMapping("/reorder")
    public ApiResponse<Void> reorder(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Void>) authErr;

        Object raw = body == null ? null : body.get("modelIds");
        if (!(raw instanceof List<?> ids)) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "modelIds required");
        }
        try {
            metadataService.reorder(ids.stream().map(String::valueOf).toList());
            return ApiResponse.ok("ok", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @DeleteMapping("/{modelId}")
    public ApiResponse<Void> delete(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable String modelId
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Void>) authErr;

        metadataService.deleteByModelId(modelId);
        return ApiResponse.ok("ok", null);
    }

    @PostMapping("/{modelId}/test")
    public ApiResponse<Map<String, Object>> testConnectivity(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable String modelId
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        String model = RequestUtils.trimOrNull(modelId);
        if (model == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "modelId required");
        }

        ChannelSelection selection;
        try {
            selection = channelRouter.select(model);
        } catch (NoChannelException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("latencyMs", 0);
            result.put("errorCode", "NO_CHANNEL");
            result.put("errorMessage", e.getMessage());
            return ApiResponse.ok(result);
        }

        ChannelMonitor.ProbeResult probe = channelMonitor.probeChannel(
                selection.channel().getId(), model, false
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", probe.ok());
        result.put("latencyMs", probe.durationMs());
        result.put("statusCode", probe.statusCode());
        result.put("responseText", probe.message());
        result.put("channelId", probe.channelId());
        result.put("keyId", probe.keyId());
        result.put("url", probe.url());
        if (!probe.ok()) {
            result.put("errorCode", probe.statusCode() > 0 ? "HTTP_" + probe.statusCode() : "CONNECT_FAILED");
            result.put("errorMessage", probe.message());
        }
        return ApiResponse.ok(result);
    }

    private Map<String, Object> toMap(AiModelMetadataEntity entity) {
        AiModelCapabilityResolver.ImageCapability imageCapability =
                capabilityResolver.resolve(entity.getModelId(), entity.getImageParsingOverride());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("modelId", entity.getModelId());
        map.put("name", entity.getName() == null ? entity.getModelId() : entity.getName());
        map.put("avatar", entity.getAvatar() == null ? "" : entity.getAvatar());
        map.put("description", entity.getDescription() == null ? "" : entity.getDescription());
        map.put("pinned", entity.isPinned());
        map.put("multiChatEnabled", entity.isMultiChatEnabled());
        map.put("sortOrder", entity.getSortOrder());
        map.put("defaultSelected", entity.isDefaultSelected());
        map.put("billingEnabled", entity.isBillingEnabled());
        map.put("requestPriceUsd", entity.getRequestPriceUsd());
        map.put("promptPriceUsd", entity.getPromptPriceUsd());
        map.put("inputPriceUsdPer1M", entity.getInputPriceUsdPer1M());
        map.put("outputPriceUsdPer1M", entity.getOutputPriceUsdPer1M());
        map.put("supportsImageParsing", imageCapability.supportsImageParsing());
        map.put("supportsImageParsingSource", imageCapability.source());
        map.put("imageParsingOverride", entity.getImageParsingOverride());
        map.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        map.put("updatedAt", entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString());
        return map;
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未经认证");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足");
        return null;
    }
}
