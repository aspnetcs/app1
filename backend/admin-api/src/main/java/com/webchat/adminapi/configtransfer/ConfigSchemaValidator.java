package com.webchat.adminapi.configtransfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.model.AiModelMetadataRepository;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.admin.ops.SysBannerRepository;
import com.webchat.platformapi.auth.group.SysUserGroupRepository;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ConfigSchemaValidator {

    private static final List<String> SUPPORTED_MODULES = List.of(
            "channels",
            "modelMetadata",
            "agents",
            "banners",
            "userGroups",
            "oauthRuntimeConfig"
    );

    private final ObjectMapper objectMapper;
    private final SsrfGuard ssrfGuard;
    private final AiChannelRepository channelRepository;
    private final AiModelMetadataRepository modelMetadataRepository;
    private final AgentRepository agentRepository;
    private final SysBannerRepository bannerRepository;
    private final SysUserGroupRepository groupRepository;

    public ConfigSchemaValidator(
            ObjectMapper objectMapper,
            SsrfGuard ssrfGuard,
            AiChannelRepository channelRepository,
            AiModelMetadataRepository modelMetadataRepository,
            AgentRepository agentRepository,
            SysBannerRepository bannerRepository,
            SysUserGroupRepository groupRepository
    ) {
        this.objectMapper = objectMapper;
        this.ssrfGuard = ssrfGuard;
        this.channelRepository = channelRepository;
        this.modelMetadataRepository = modelMetadataRepository;
        this.agentRepository = agentRepository;
        this.bannerRepository = bannerRepository;
        this.groupRepository = groupRepository;
    }

    public List<String> supportedModules() {
        return SUPPORTED_MODULES;
    }

    public void validatePayload(ConfigTransferPayload payload, int schemaVersion) {
        if (payload == null) {
            throw new ConfigTransferService.ConfigTransferException("payload required");
        }
        if (payload.schemaVersion() != schemaVersion) {
            throw new ConfigTransferService.ConfigTransferException("unsupported schemaVersion");
        }
    }

    /**
     * Produce a best-effort payload summary that can be shown to operators even when schema is incompatible.
     * This method must never throw.
     */
    public Map<String, Object> inspectPayload(ConfigTransferPayload payload, int expectedSchemaVersion) {
        int payloadSchemaVersion = payload == null ? 0 : payload.schemaVersion();
        boolean compatible = payload != null && payloadSchemaVersion == expectedSchemaVersion;
        List<String> rawModules = payload == null ? List.of() : (payload.modules() == null ? List.of() : payload.modules());
        List<String> normalizedModules;
        List<String> warnings = new ArrayList<>();
        try {
            normalizedModules = normalizeModules(rawModules, false);
        } catch (RuntimeException e) {
            normalizedModules = List.of();
            warnings.add(e.getMessage() == null ? "invalid modules" : e.getMessage());
        }

        return Map.of(
                "schemaVersion", payloadSchemaVersion,
                "expectedSchemaVersion", expectedSchemaVersion,
                "compatible", compatible,
                "moduleCount", normalizedModules.size(),
                "modules", normalizedModules,
                "exportedAt", payload == null ? "" : (payload.exportedAt() == null ? "" : payload.exportedAt()),
                "warnings", warnings
        );
    }

    public List<String> normalizeModules(List<String> rawModules, boolean allowDefaultAll) {
        Set<String> values = new LinkedHashSet<>();
        boolean provided = rawModules != null && !rawModules.isEmpty();
        if (rawModules != null) {
            for (String raw : rawModules) {
                if (raw == null) {
                    continue;
                }
                for (String item : raw.split(",")) {
                    String normalized = item.trim();
                    if (!normalized.isEmpty() && SUPPORTED_MODULES.contains(normalized)) {
                        values.add(normalized);
                    }
                }
            }
        }
        if (values.isEmpty()) {
            if (allowDefaultAll && !provided) {
                values.addAll(SUPPORTED_MODULES);
            } else {
                throw new ConfigTransferService.ConfigTransferException("at least one valid module is required");
            }
        }
        return List.copyOf(values);
    }

    public Map<String, Object> previewModule(String module, Object raw, List<String> warnings) {
        if ("oauthRuntimeConfig".equals(module)) {
            return Map.of(
                    "module", module,
                    "total", raw instanceof Map<?, ?> ? 1 : 0,
                    "create", 0,
                    "update", raw instanceof Map<?, ?> ? 1 : 0,
                    "skip", 0
            );
        }

        List<Map<String, Object>> items = raw instanceof List<?> list
                ? objectMapper.convertValue(list, new TypeReference<>() {
        })
                : List.of();
        int create = 0;
        int update = 0;
        int skipped = 0;
        for (Map<String, Object> item : items) {
            try {
                validateItem(module, item);
                if (exists(module, item)) {
                    update++;
                } else {
                    create++;
                }
            } catch (ConfigTransferService.ConfigTransferException e) {
                skipped++;
                warnings.add(module + ": " + e.getMessage());
            }
        }
        if ("channels".equals(module)) {
            warnings.add("channels export/import excludes secret keys");
        }
        if ("userGroups".equals(module)) {
            warnings.add("userGroups import excludes memberUserIds");
        }
        return Map.of(
                "module", module,
                "total", items.size(),
                "create", create,
                "update", update,
                "skip", skipped
        );
    }

    public void validateItem(String module, Map<String, Object> item) {
        switch (module) {
            case "channels" -> validateChannelItem(item);
            case "modelMetadata" -> validateModelMetadataItem(item);
            case "agents" -> {
                if (ConfigTransferValueSupport.text(item.get("name")) == null) {
                    throw new ConfigTransferService.ConfigTransferException("agent name missing");
                }
                if (RequestUtils.firstNonBlank(
                        ConfigTransferValueSupport.text(item.get("modelId")),
                        ConfigTransferValueSupport.text(item.get("model_id"))
                ) == null) {
                    throw new ConfigTransferService.ConfigTransferException("agent modelId missing");
                }
            }
            case "banners" -> {
                if (ConfigTransferValueSupport.text(item.get("title")) == null
                        || ConfigTransferValueSupport.text(item.get("content")) == null) {
                    throw new ConfigTransferService.ConfigTransferException("banner title/content missing");
                }
            }
            case "userGroups" -> {
                if (ConfigTransferValueSupport.text(item.get("name")) == null) {
                    throw new ConfigTransferService.ConfigTransferException("group name missing");
                }
            }
            default -> {
            }
        }
    }

    public void validateChannelItem(Map<String, Object> item) {
        String name = ConfigTransferValueSupport.text(item.get("name"));
        String type = ConfigTransferValueSupport.text(item.get("type"));
        String baseUrl = ConfigTransferValueSupport.text(item.get("baseUrl"));
        if (name == null || type == null || baseUrl == null) {
            throw new ConfigTransferService.ConfigTransferException("channel name/type/baseUrl missing");
        }
        try {
            ssrfGuard.assertAllowedBaseUrl(baseUrl);
        } catch (SsrfGuard.SsrfException e) {
            throw new ConfigTransferService.ConfigTransferException("channel baseUrl rejected");
        }
        if (ConfigTransferValueSupport.intValue(item.get("weight"), 1) <= 0) {
            throw new ConfigTransferService.ConfigTransferException("channel weight invalid");
        }
        if (ConfigTransferValueSupport.intValue(item.get("maxConcurrent"), 1) <= 0) {
            throw new ConfigTransferService.ConfigTransferException("channel maxConcurrent invalid");
        }
    }

    public List<Map<String, Object>> readList(ConfigTransferPayload payload, String module) {
        Object raw = payload.data() == null ? null : payload.data().get(module);
        if (raw == null) {
            return List.of();
        }
        return objectMapper.convertValue(raw, new TypeReference<>() {
        });
    }

    public Map<String, Object> readMap(ConfigTransferPayload payload, String module) {
        Object raw = payload.data() == null ? null : payload.data().get(module);
        if (raw == null) {
            return Map.of();
        }
        return objectMapper.convertValue(raw, new TypeReference<>() {
        });
    }

    private void validateModelMetadataItem(Map<String, Object> item) {
        String modelId = ConfigTransferValueSupport.text(item.get("modelId"));
        if (modelId == null) {
            throw new ConfigTransferService.ConfigTransferException("modelId missing");
        }
    }

    private boolean exists(String module, Map<String, Object> item) {
        return switch (module) {
            case "channels" -> channelRepository.findFirstByNameAndTypeAndBaseUrl(
                    ConfigTransferValueSupport.text(item.get("name")),
                    ConfigTransferValueSupport.text(item.get("type")),
                    ConfigTransferValueSupport.text(item.get("baseUrl"))
            ).isPresent();
            case "modelMetadata" -> {
                String modelId = ConfigTransferValueSupport.text(item.get("modelId"));
                yield modelId != null && modelMetadataRepository.findByModelId(modelId).isPresent();
            }
            case "agents" -> {
                UUID id = ConfigTransferValueSupport.uuid(item.get("id"));
                yield id != null && agentRepository.findByIdAndDeletedAtIsNull(id).isPresent();
            }
            case "banners" -> {
                UUID id = ConfigTransferValueSupport.uuid(item.get("id"));
                yield id != null && bannerRepository.findById(id).isPresent();
            }
            case "userGroups" -> {
                UUID id = ConfigTransferValueSupport.uuid(item.get("id"));
                yield id != null && groupRepository.findById(id).isPresent();
            }
            default -> false;
        };
    }
}
