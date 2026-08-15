package com.webchat.adminapi.configtransfer;

import com.webchat.adminapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.adminapi.group.UserGroupAdminService;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.model.AiModelMetadataRepository;
import com.webchat.platformapi.ai.model.AiModelMetadataService;
import com.webchat.platformapi.ai.model.ModelMetadataRequest;
import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.admin.ops.SysBannerRepository;
import com.webchat.platformapi.auth.group.SysUserGroupRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfigImportService {

    private final AiChannelRepository channelRepository;
    private final AiModelMetadataRepository modelMetadataRepository;
    private final AgentRepository agentRepository;
    private final SysBannerRepository bannerRepository;
    private final SysUserGroupRepository groupRepository;
    private final AiModelMetadataService modelMetadataService;
    private final AgentService agentService;
    private final BannerService bannerService;
    private final UserGroupAdminService userGroupService;
    private final OAuthRuntimeConfigService oauthRuntimeConfigService;
    private final ConfigSchemaValidator configSchemaValidator;

    public ConfigImportService(
            AiChannelRepository channelRepository,
            AiModelMetadataRepository modelMetadataRepository,
            AgentRepository agentRepository,
            SysBannerRepository bannerRepository,
            SysUserGroupRepository groupRepository,
            AiModelMetadataService modelMetadataService,
            AgentService agentService,
            BannerService bannerService,
            UserGroupAdminService userGroupService,
            OAuthRuntimeConfigService oauthRuntimeConfigService,
            ConfigSchemaValidator configSchemaValidator
    ) {
        this.channelRepository = channelRepository;
        this.modelMetadataRepository = modelMetadataRepository;
        this.agentRepository = agentRepository;
        this.bannerRepository = bannerRepository;
        this.groupRepository = groupRepository;
        this.modelMetadataService = modelMetadataService;
        this.agentService = agentService;
        this.bannerService = bannerService;
        this.userGroupService = userGroupService;
        this.oauthRuntimeConfigService = oauthRuntimeConfigService;
        this.configSchemaValidator = configSchemaValidator;
    }

    public Map<String, Object> importModule(String module, ConfigTransferPayload payload, List<String> warnings) {
        return switch (module) {
            case "channels" -> importChannels(configSchemaValidator.readList(payload, "channels"), warnings);
            case "modelMetadata" -> importModelMetadata(configSchemaValidator.readList(payload, "modelMetadata"));
            case "agents" -> importAgents(configSchemaValidator.readList(payload, "agents"));
            case "banners" -> importBanners(configSchemaValidator.readList(payload, "banners"));
            case "userGroups" -> importUserGroups(configSchemaValidator.readList(payload, "userGroups"));
            case "oauthRuntimeConfig" -> importOAuthRuntimeConfig(configSchemaValidator.readMap(payload, "oauthRuntimeConfig"));
            default -> result(module, 0, 0, 0, 0);
        };
    }

    private Map<String, Object> importChannels(List<Map<String, Object>> items, List<String> warnings) {
        int created = 0;
        int updated = 0;
        Map<Long, Long> idMap = new LinkedHashMap<>();
        Map<Long, Long> fallbackMap = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            configSchemaValidator.validateChannelItem(item);
            String name = ConfigTransferValueSupport.text(item.get("name"));
            String type = ConfigTransferValueSupport.text(item.get("type"));
            String baseUrl = ConfigTransferValueSupport.text(item.get("baseUrl"));
            if (name == null || type == null || baseUrl == null) {
                throw new ConfigTransferService.ConfigTransferException("channel entry missing required fields");
            }
            AiChannelEntity entity = channelRepository.findFirstByNameAndTypeAndBaseUrl(name, type, baseUrl).orElseGet(AiChannelEntity::new);
            boolean isNew = entity.getId() == null;
            entity.setName(name);
            entity.setType(type);
            entity.setBaseUrl(baseUrl);
            entity.setModels(ConfigTransferValueSupport.text(item.get("models")));
            entity.setModelMapping(ConfigTransferValueSupport.mapOfStrings(item.get("modelMapping")));
            entity.setEnabled(ConfigTransferValueSupport.bool(item.get("enabled"), true));
            entity.setPriority(ConfigTransferValueSupport.intValue(item.get("priority"), 0));
            entity.setWeight(Math.max(1, ConfigTransferValueSupport.intValue(item.get("weight"), 1)));
            entity.setMaxConcurrent(Math.max(1, ConfigTransferValueSupport.intValue(item.get("maxConcurrent"), 4)));
            entity.setStatus(ConfigTransferValueSupport.intValue(item.get("status"), 1));
            entity.setTestModel(ConfigTransferValueSupport.text(item.get("testModel")));
            // Secrets must never be imported via config transfer.
            entity.setExtraConfig(ConfigTransferValueSupport.mapOfObjects(
                    ConfigTransferSecretRedactor.redact(item.get("extraConfig"))
            ));
            AiChannelEntity saved = channelRepository.save(entity);

            Long exportId = ConfigTransferValueSupport.longValue(item.get("exportId"));
            Long fallbackId = ConfigTransferValueSupport.longValue(item.get("fallbackChannelId"));
            if (exportId != null) {
                idMap.put(exportId, saved.getId());
                if (fallbackId != null) {
                    fallbackMap.put(saved.getId(), fallbackId);
                }
            }

            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        for (Map.Entry<Long, Long> entry : fallbackMap.entrySet()) {
            AiChannelEntity entity = channelRepository.findById(entry.getKey()).orElse(null);
            if (entity == null) {
                continue;
            }
            Long mappedFallback = idMap.get(entry.getValue());
            if (mappedFallback == null) {
                warnings.add("channel fallback skipped for exportId=" + entry.getValue());
            } else {
                entity.setFallbackChannelId(mappedFallback);
                channelRepository.save(entity);
            }
        }
        return result("channels", items.size(), created, updated, 0);
    }

    private Map<String, Object> importModelMetadata(List<Map<String, Object>> items) {
        int created = 0;
        int updated = 0;
        for (Map<String, Object> item : items) {
            configSchemaValidator.validateItem("modelMetadata", item);
            String modelId = ConfigTransferValueSupport.text(item.get("modelId"));
            boolean isNew = modelMetadataRepository.findByModelId(modelId).isEmpty();
            ModelMetadataRequest request = new ModelMetadataRequest();
            request.setName(ConfigTransferValueSupport.text(item.get("name")));
            request.setAvatar(ConfigTransferValueSupport.text(item.get("avatar")));
            request.setDescription(ConfigTransferValueSupport.text(item.get("description")));
            request.setPinned(ConfigTransferValueSupport.boolOrNull(item.get("pinned")));
            request.setSortOrder(ConfigTransferValueSupport.integer(item.get("sortOrder")));
            request.setDefaultSelected(ConfigTransferValueSupport.boolOrNull(item.get("defaultSelected")));
            modelMetadataService.upsert(modelId, request);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        return result("modelMetadata", items.size(), created, updated, 0);
    }

    private Map<String, Object> importAgents(List<Map<String, Object>> items) {
        int created = 0;
        int updated = 0;
        for (Map<String, Object> item : items) {
            configSchemaValidator.validateItem("agents", item);
            UUID id = ConfigTransferValueSupport.uuid(item.get("id"));
            boolean isNew = id == null || agentRepository.findByIdAndDeletedAtIsNull(id).isEmpty();
            if (isNew) {
                agentService.createForAdmin(item);
            } else {
                agentService.updateForAdmin(id, item);
            }
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        return result("agents", items.size(), created, updated, 0);
    }

    private Map<String, Object> importBanners(List<Map<String, Object>> items) {
        int created = 0;
        int updated = 0;
        for (Map<String, Object> item : items) {
            configSchemaValidator.validateItem("banners", item);
            UUID id = ConfigTransferValueSupport.uuid(item.get("id"));
            boolean isNew = id == null || bannerRepository.findById(id).isEmpty();
            if (isNew) {
                bannerService.create(item);
            } else {
                bannerService.update(id, item);
            }
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        return result("banners", items.size(), created, updated, 0);
    }

    private Map<String, Object> importUserGroups(List<Map<String, Object>> items) {
        int created = 0;
        int updated = 0;
        for (Map<String, Object> item : items) {
            configSchemaValidator.validateItem("userGroups", item);
            UUID id = ConfigTransferValueSupport.uuid(item.get("id"));
            Map<String, Object> body = new LinkedHashMap<>(item);
            body.remove("memberUserIds");
            body.remove("member_user_ids");
            boolean isNew = id == null || groupRepository.findById(id).isEmpty();
            if (isNew) {
                userGroupService.create(body);
            } else {
                userGroupService.update(id, body);
            }
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        return result("userGroups", items.size(), created, updated, 0);
    }

    private Map<String, Object> importOAuthRuntimeConfig(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return result("oauthRuntimeConfig", 0, 0, 0, 0);
        }
        Map<String, OAuthRuntimeConfigService.ProviderOverride> providers = new LinkedHashMap<>();
        if (item.get("providers") instanceof Map<?, ?> rawProviders) {
            for (Map.Entry<?, ?> entry : rawProviders.entrySet()) {
                String key = ConfigTransferValueSupport.text(entry.getKey());
                if (key == null) {
                    continue;
                }
                Map<String, Object> providerMap = ConfigTransferValueSupport.mapOfObjects(entry.getValue());
                providers.put(key, new OAuthRuntimeConfigService.ProviderOverride(
                        ConfigTransferValueSupport.boolOrNull(providerMap.get("enabled")),
                        ConfigTransferValueSupport.boolOrNull(providerMap.get("allowAdminLogin"))
                ));
            }
        }
        try {
            oauthRuntimeConfigService.save(new OAuthRuntimeConfigService.StoredOverrides(
                    ConfigTransferValueSupport.boolOrNull(item.get("enabled")),
                    ConfigTransferValueSupport.boolOrNull(item.get("allowAdminLogin")),
                    providers
            ));
        } catch (OAuthRuntimeConfigService.OAuthConfigException e) {
            throw new ConfigTransferService.ConfigTransferException(e.getMessage());
        }
        return result("oauthRuntimeConfig", 1, 0, 1, 0);
    }

    private Map<String, Object> result(String module, int total, int created, int updated, int skipped) {
        return Map.of("module", module, "total", total, "created", created, "updated", updated, "skipped", skipped);
    }
}
