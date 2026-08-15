package com.webchat.adminapi.feature;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record FeatureDefinition(String key, List<FeatureField> fields) {

    Map<String, Object> readConfig(Environment environment, SysConfigService sysConfigService) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("featureKey", key);
        for (FeatureField field : fields) {
            payload.put(field.key(), field.readValue(environment, sysConfigService));
        }
        return payload;
    }

    void writeConfig(Map<String, Object> body, SysConfigService sysConfigService) {
        for (FeatureField field : fields) {
            if (field.readOnly() || !body.containsKey(field.key())) {
                continue;
            }
            String propertyKey = field.propertyKey();
            if (propertyKey == null || propertyKey.isBlank()) {
                continue;
            }
            sysConfigService.set(propertyKey, field.serialize(body.get(field.key())));
        }
    }
}
