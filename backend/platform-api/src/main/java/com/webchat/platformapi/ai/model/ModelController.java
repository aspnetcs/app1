package com.webchat.platformapi.ai.model;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.credits.CreditsSystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ChannelRouter channelRouter;
    private final AiModelMetadataService metadataService;
    private final AiModelCapabilityResolver capabilityResolver;
    private final RolePolicyService rolePolicyService;
    private final CreditsSystemConfig creditsSystemConfig;
    private final boolean metadataEnabled;

    public ModelController(ChannelRouter channelRouter,
                           AiModelMetadataService metadataService,
                           AiModelCapabilityResolver capabilityResolver,
                           RolePolicyService rolePolicyService,
                           @Nullable CreditsSystemConfig creditsSystemConfig,
                           @Value("${platform.model-metadata.enabled:true}") boolean metadataEnabled) {
        this.channelRouter = channelRouter;
        this.metadataService = metadataService;
        this.capabilityResolver = capabilityResolver;
        this.rolePolicyService = rolePolicyService;
        this.creditsSystemConfig = creditsSystemConfig;
        this.metadataEnabled = metadataEnabled;
    }

    /**
     * Return the catalog of models, honoring channel metadata and display overrides.
     */
    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role) {
        List<AiChannelEntity> channels;
        try {
            channels = channelRouter.listRoutableChannels();
        } catch (Exception e) {
            log.error("[model] failed to load routable channels", e);
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "model catalog unavailable");
        }

        Map<String, Map<String, Object>> metaMap = new LinkedHashMap<>();
        Set<String> modelIds = new TreeSet<>();
        for (AiChannelEntity ch : channels) {
            if (ch == null) continue;
            collectModels(modelIds, ch.getModels());
            collectModelMappingKeys(modelIds, ch.getModelMapping());
            collectModelMeta(metaMap, ch.getExtraConfig());
        }
        Set<String> allowed = Set.of();
        if (userId != null) {
            try {
                allowed = rolePolicyService.resolveAllowedModels(userId, role);
            } catch (Exception e) {
                log.warn("[model] role policy unavailable for userId={}", userId, e);
                return ApiResponse.error(ErrorCodes.SERVER_ERROR, "model policy unavailable");
            }
        }
        if (!allowed.isEmpty()) {
            modelIds.retainAll(allowed);
        }

        Map<String, AiModelMetadataEntity> displayMeta = metadataEnabled
                ? metadataService.findByModelIds(modelIds)
                : Map.of();

        boolean showPricing = creditsSystemConfig != null
                && creditsSystemConfig.isCreditsSystemEnabled()
                && !creditsSystemConfig.isFreeModeEnabled();

        List<ModelView> views = modelIds.stream()
                .map(id -> ModelView.from(id, metaMap.get(id), displayMeta.get(id), capabilityResolver, showPricing))
                .collect(Collectors.toList());

        views.sort(MODEL_COMPARATOR);
        List<Map<String, Object>> result = new ArrayList<>(views.size());
        for (ModelView view : views) {
            result.add(view.toMap());
        }

        return ApiResponse.ok(result);
    }

    // ======================================== helpers ============================================

    /**
     * Collect model declarations from the channel's models field.
     * Wildcard entries remain visible so clients can select the same routable
     * contracts that ChannelRouter accepts.
     */
    private static void collectModels(Set<String> out, String models) {
        if (models == null || models.isBlank()) return;
        for (String part : models.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
    }

    /**
     * Collect model declarations from the channel's modelMapping keys.
     * Wildcard mapping keys remain visible because they define valid routable
     * model contracts for downstream clients.
     */
    private static void collectModelMappingKeys(Set<String> out, Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) return;
        for (String key : mapping.keySet()) {
            if (key != null && !key.isBlank()) out.add(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectModelMeta(Map<String, Map<String, Object>> metaMap, Map<String, Object> extraConfig) {
        if (extraConfig == null) return;
        Object raw = extraConfig.get("modelMeta");
        if (!(raw instanceof Map)) return;
        Map<String, Object> meta = (Map<String, Object>) raw;
        for (Map.Entry<String, Object> e : meta.entrySet()) {
            if (e.getValue() instanceof Map) {
                metaMap.putIfAbsent(e.getKey(), (Map<String, Object>) e.getValue());
            }
        }
    }

    private static final Comparator<ModelView> MODEL_COMPARATOR = Comparator
            .comparing(ModelView::isPinned).reversed()
            .thenComparingInt(ModelView::getSortOrder)
            .thenComparing(ModelView::getName, String.CASE_INSENSITIVE_ORDER);

    private static final class ModelView {

        private final String id;
        private final String name;
        private final String avatar;
        private final String description;
        private final boolean pinned;
        private final boolean multiChatEnabled;
        private final int sortOrder;
        private final boolean defaultSelected;
        private final boolean supportsImageParsing;
        private final String supportsImageParsingSource;
        private final boolean billingEnabled;
        private final BigDecimal requestPriceUsd;
        private final BigDecimal promptPriceUsd;
        private final BigDecimal inputPriceUsdPer1M;
        private final BigDecimal outputPriceUsdPer1M;
        private final boolean showPricing;

        private ModelView(String id, String name, String avatar, String description,
                         boolean pinned, boolean multiChatEnabled, int sortOrder, boolean defaultSelected,
                         boolean supportsImageParsing, String supportsImageParsingSource,
                         boolean billingEnabled, BigDecimal requestPriceUsd, BigDecimal promptPriceUsd,
                         BigDecimal inputPriceUsdPer1M, BigDecimal outputPriceUsdPer1M, boolean showPricing) {
            this.id = id;
            this.name = name;
            this.avatar = avatar;
            this.description = description;
            this.pinned = pinned;
            this.multiChatEnabled = multiChatEnabled;
            this.sortOrder = sortOrder;
            this.defaultSelected = defaultSelected;
            this.supportsImageParsing = supportsImageParsing;
            this.supportsImageParsingSource = supportsImageParsingSource;
            this.billingEnabled = billingEnabled;
            this.requestPriceUsd = requestPriceUsd;
            this.promptPriceUsd = promptPriceUsd;
            this.inputPriceUsdPer1M = inputPriceUsdPer1M;
            this.outputPriceUsdPer1M = outputPriceUsdPer1M;
            this.showPricing = showPricing;
        }

        public String getName() {
            return name;
        }

        public boolean isPinned() {
            return pinned;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("avatar", avatar);
            map.put("description", description);
            map.put("pinned", pinned);
            map.put("multiChatEnabled", multiChatEnabled);
            map.put("sortOrder", sortOrder);
            map.put("defaultSelected", defaultSelected);
            map.put("isDefault", defaultSelected);
            map.put("supportsImageParsing", supportsImageParsing);
            map.put("supportsImageParsingSource", supportsImageParsingSource);
            if (showPricing) {
                map.put("billingEnabled", billingEnabled);
                if (billingEnabled) {
                    map.put("requestPriceUsd", requestPriceUsd);
                    map.put("promptPriceUsd", promptPriceUsd);
                    map.put("inputPriceUsdPer1M", inputPriceUsdPer1M);
                    map.put("outputPriceUsdPer1M", outputPriceUsdPer1M);
                }
            }
            return map;
        }

        private static ModelView from(String modelId, Map<String, Object> channelMeta,
                                       AiModelMetadataEntity displayMeta,
                                       AiModelCapabilityResolver capabilityResolver,
                                       boolean showPricing) {
            String name = defaultChannelName(channelMeta, modelId);
            String avatar = defaultChannelAvatar(channelMeta, modelId);
            String description = defaultChannelDescription(channelMeta);

            if (displayMeta != null) {
                if (displayMeta.getName() != null) name = displayMeta.getName();
                if (displayMeta.getAvatar() != null) avatar = displayMeta.getAvatar();
                if (displayMeta.getDescription() != null) description = displayMeta.getDescription();
            }

            boolean pinned = displayMeta != null && displayMeta.isPinned();
            boolean multiChatEnabled = displayMeta == null || displayMeta.isMultiChatEnabled();
            int sortOrder = displayMeta != null ? displayMeta.getSortOrder() : 0;
            boolean defaultSelected = displayMeta != null && displayMeta.isDefaultSelected();
            Boolean imageParsingOverride = displayMeta != null ? displayMeta.getImageParsingOverride() : null;
            AiModelCapabilityResolver.ImageCapability imageCapability =
                    capabilityResolver.resolve(modelId, imageParsingOverride);
            boolean billingEnabled = displayMeta != null && displayMeta.isBillingEnabled();
            BigDecimal requestPriceUsd = displayMeta != null ? displayMeta.getRequestPriceUsd() : null;
            BigDecimal promptPriceUsd = displayMeta != null ? displayMeta.getPromptPriceUsd() : null;
            BigDecimal inputPriceUsdPer1M = displayMeta != null ? displayMeta.getInputPriceUsdPer1M() : null;
            BigDecimal outputPriceUsdPer1M = displayMeta != null ? displayMeta.getOutputPriceUsdPer1M() : null;
            return new ModelView(modelId, name, avatar, description, pinned, multiChatEnabled, sortOrder,
                    defaultSelected, imageCapability.supportsImageParsing(), imageCapability.source(),
                    billingEnabled, requestPriceUsd, promptPriceUsd, inputPriceUsdPer1M, outputPriceUsdPer1M, showPricing);
        }

        private static String defaultChannelName(Map<String, Object> channelMeta, String modelId) {
            String name = getString(channelMeta, "name");
            return name != null ? name : prettifyModelName(modelId);
        }

        private static String defaultChannelAvatar(Map<String, Object> channelMeta, String modelId) {
            String avatar = getString(channelMeta, "avatar");
            return avatar != null ? avatar : inferAvatar(modelId);
        }

        private static String defaultChannelDescription(Map<String, Object> channelMeta) {
            String description = getString(channelMeta, "description");
            return description != null ? description : "";
        }

        private static String getString(Map<String, Object> meta, String key) {
            if (meta == null) return null;
            Object value = meta.get(key);
            return value != null ? String.valueOf(value) : null;
        }
    }

    /**
     * Infer an avatar key from a model identifier so we can show a provider icon.
     */
    static String inferAvatar(String modelId) {
        if (modelId == null) return "default";
        String lower = modelId.toLowerCase();
        if (lower.contains("gpt") || lower.contains("o1") || lower.contains("o3") || lower.contains("o4"))
            return "openai";
        if (lower.contains("claude")) return "claude";
        if (lower.contains("gemini")) return "gemini";
        if (lower.contains("deepseek")) return "deepseek";
        if (lower.contains("qwen")) return "qwen";
        if (lower.contains("llama") || lower.contains("meta")) return "meta";
        if (lower.contains("mistral")) return "mistral";
        if (lower.contains("grok")) return "grok";
        if (lower.contains("doubao")) return "doubao";
        if (lower.contains("kimi") || lower.contains("moonshot")) return "kimi";
        if (lower.contains("glm") || lower.contains("chatglm")) return "zhipu";
        if (lower.contains("yi")) return "yi";
        if (lower.contains("hunyuan")) return "hunyuan";
        if (lower.contains("ernie")) return "ernie";
        return "default";
    }

    /**
     * Prettify a model ID into something more readable for clients.
     */
    static String prettifyModelName(String modelId) {
        if (modelId == null || modelId.isBlank()) return modelId;
        String[] parts = modelId.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            if (p.isEmpty()) continue;
            if (Character.isDigit(p.charAt(0)) || p.matches("\\d.*")) {
                sb.append(p);
            } else {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }
}
