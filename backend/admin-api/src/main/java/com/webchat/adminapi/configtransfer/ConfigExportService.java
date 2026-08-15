package com.webchat.adminapi.configtransfer;

import com.webchat.adminapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.adminapi.group.UserGroupAdminService;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.agent.AgentScope;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.model.AiModelMetadataRepository;
import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.admin.ops.SysBannerRepository;
import com.webchat.platformapi.auth.group.SysUserGroupEntity;
import com.webchat.platformapi.auth.group.SysUserGroupRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigExportService {

    private final AiChannelRepository channelRepository;
    private final AiModelMetadataRepository modelMetadataRepository;
    private final AgentRepository agentRepository;
    private final SysBannerRepository bannerRepository;
    private final SysUserGroupRepository groupRepository;
    private final AgentService agentService;
    private final BannerService bannerService;
    private final UserGroupAdminService userGroupService;
    private final OAuthRuntimeConfigService oauthRuntimeConfigService;

    public ConfigExportService(
            AiChannelRepository channelRepository,
            AiModelMetadataRepository modelMetadataRepository,
            AgentRepository agentRepository,
            SysBannerRepository bannerRepository,
            SysUserGroupRepository groupRepository,
            AgentService agentService,
            BannerService bannerService,
            UserGroupAdminService userGroupService,
            OAuthRuntimeConfigService oauthRuntimeConfigService
    ) {
        this.channelRepository = channelRepository;
        this.modelMetadataRepository = modelMetadataRepository;
        this.agentRepository = agentRepository;
        this.bannerRepository = bannerRepository;
        this.groupRepository = groupRepository;
        this.agentService = agentService;
        this.bannerService = bannerService;
        this.userGroupService = userGroupService;
        this.oauthRuntimeConfigService = oauthRuntimeConfigService;
    }

    public Map<String, Object> featureConfig(List<String> supportedModules, int schemaVersion) {
        return Map.of(
                "featureKey", "platform.config-transfer.enabled",
                "schemaVersion", schemaVersion,
                "supportsSecretsExport", false,
                "supportedModules", supportedModules,
                "notes", List.of(
                        "Secrets are excluded by default.",
                        "User-owned data and group memberships are excluded.",
                        "Import mode is merge/upsert only."
                )
        );
    }

    public Object exportModule(String module) {
        return switch (module) {
            case "channels" -> exportChannels();
            case "modelMetadata" -> exportModelMetadata();
            case "agents" -> exportAgents();
            case "banners" -> exportBanners();
            case "userGroups" -> exportUserGroups();
            case "oauthRuntimeConfig" -> exportOAuthRuntimeConfig();
            default -> List.of();
        };
    }

    public Map<String, Object> exportMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("supportsSecretsExport", false);
        meta.put("secretsIncluded", false);
        meta.put("notes", List.of(
                "Channel keys are not exported.",
                "User prompts, user presets and group members are not exported."
        ));
        return meta;
    }

    private List<Map<String, Object>> exportChannels() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AiChannelEntity entity : channelRepository.findAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("exportId", entity.getId());
            item.put("name", entity.getName());
            item.put("type", entity.getType());
            item.put("baseUrl", entity.getBaseUrl());
            item.put("models", entity.getModels());
            item.put("modelMapping", entity.getModelMapping());
            item.put("enabled", entity.isEnabled());
            item.put("priority", entity.getPriority());
            item.put("weight", entity.getWeight());
            item.put("maxConcurrent", entity.getMaxConcurrent());
            item.put("status", entity.getStatus());
            item.put("testModel", entity.getTestModel());
            item.put("fallbackChannelId", entity.getFallbackChannelId());
            // Secrets must never be exported, even if operators accidentally stored them in extraConfig.
            item.put("extraConfig", ConfigTransferSecretRedactor.redact(entity.getExtraConfig()));
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> exportModelMetadata() {
        return modelMetadataRepository.findAll().stream().map(entity -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelId", entity.getModelId());
            item.put("name", entity.getName());
            item.put("avatar", entity.getAvatar());
            item.put("description", entity.getDescription());
            item.put("pinned", entity.isPinned());
            item.put("sortOrder", entity.getSortOrder());
            item.put("defaultSelected", entity.isDefaultSelected());
            return item;
        }).toList();
    }

    private List<Map<String, Object>> exportAgents() {
        return agentRepository.findByScopeAndEnabledTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(AgentScope.SYSTEM)
                .stream()
                .map(AgentService::toAdminDto)
                .toList();
    }

    private List<Map<String, Object>> exportBanners() {
        return bannerRepository.findAll().stream()
                .filter(entity -> entity.getDeletedAt() == null)
                .map(BannerService::toDto)
                .toList();
    }

    private List<Map<String, Object>> exportUserGroups() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysUserGroupEntity entity : groupRepository.findAll()) {
            if (entity.getDeletedAt() != null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entity.getId());
            item.put("name", entity.getName());
            item.put("description", entity.getDescription());
            item.put("allowedModels", ConfigTransferValueSupport.splitCsv(entity.getAllowedModels()));
            item.put("featureFlags", ConfigTransferValueSupport.splitCsv(entity.getFeatureFlags()));
            item.put("chatRateLimitPerMinute", entity.getChatRateLimitPerMinute());
            item.put("enabled", entity.isEnabled());
            item.put("sortOrder", entity.getSortOrder());
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> exportOAuthRuntimeConfig() {
        OAuthRuntimeConfigService.RuntimeConfig config = oauthRuntimeConfigService.currentConfig();
        Map<String, Object> providers = new LinkedHashMap<>();
        for (Map.Entry<String, OAuthRuntimeConfigService.ProviderConfig> entry : config.providers().entrySet()) {
            providers.put(entry.getKey(), Map.of(
                    "enabled", entry.getValue().enabled(),
                    "allowAdminLogin", entry.getValue().allowAdminLogin()
            ));
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("enabled", config.enabled());
        item.put("allowAdminLogin", config.allowAdminLogin());
        item.put("providers", providers);
        return item;
    }
}
