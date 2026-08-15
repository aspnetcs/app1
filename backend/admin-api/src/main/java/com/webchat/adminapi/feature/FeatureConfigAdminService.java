package com.webchat.adminapi.feature;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.config.SysConfigService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class FeatureConfigAdminService {

    private final Environment environment;
    private final SysConfigService sysConfigService;
    private final Map<String, FeatureDefinition> definitions;

    public FeatureConfigAdminService(Environment environment, SysConfigService sysConfigService) {
        this.environment = environment;
        this.sysConfigService = sysConfigService;
        this.definitions = FeatureConfigCatalog.createDefinitions();
    }

    public Map<String, Object> getConfig(String featureKey) {
        FeatureDefinition definition = requireDefinition(featureKey);
        return definition.readConfig(environment, sysConfigService);
    }

    public ApiResponse<Map<String, Object>> updateConfig(String featureKey, Map<String, Object> body) {
        FeatureDefinition definition = requireDefinition(featureKey);
        if (body == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "config body required");
        }
        try {
            definition.writeConfig(body, sysConfigService);
            return ApiResponse.ok(definition.readConfig(environment, sysConfigService));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    private FeatureDefinition requireDefinition(String featureKey) {
        FeatureDefinition definition = definitions.get(normalizeFeatureKey(featureKey));
        if (definition == null) {
            throw new IllegalArgumentException("unsupported feature: " + featureKey);
        }
        return definition;
    }

    private static String normalizeFeatureKey(String featureKey) {
        return featureKey == null ? "" : featureKey.trim().toLowerCase(Locale.ROOT);
    }
}
