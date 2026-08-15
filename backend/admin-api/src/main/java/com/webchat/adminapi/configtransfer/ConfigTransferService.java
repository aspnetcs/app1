package com.webchat.adminapi.configtransfer;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigTransferService {

    public static final int SCHEMA_VERSION = 1;

    private final ConfigSchemaValidator configSchemaValidator;
    private final ConfigExportService configExportService;
    private final ConfigImportService configImportService;

    public ConfigTransferService(
            ConfigSchemaValidator configSchemaValidator,
            ConfigExportService configExportService,
            ConfigImportService configImportService
    ) {
        this.configSchemaValidator = configSchemaValidator;
        this.configExportService = configExportService;
        this.configImportService = configImportService;
    }

    public Map<String, Object> featureConfig() {
        return configExportService.featureConfig(configSchemaValidator.supportedModules(), SCHEMA_VERSION);
    }

    public ConfigTransferPayload exportPayload(List<String> rawModules, boolean includeSecrets) {
        List<String> modules = configSchemaValidator.normalizeModules(rawModules, true);
        Map<String, Object> data = new LinkedHashMap<>();
        for (String module : modules) {
            data.put(module, configExportService.exportModule(module));
        }
        return new ConfigTransferPayload(
                SCHEMA_VERSION,
                Instant.now().toString(),
                false,
                modules,
                data,
                configExportService.exportMeta()
        );
    }

    public ConfigImportPreview preview(ConfigTransferPayload payload) {
        configSchemaValidator.validatePayload(payload, SCHEMA_VERSION);
        List<String> warnings = new ArrayList<>();
        if (payload.includeSecrets()) {
            warnings.add("includeSecrets is ignored in this workspace");
        }
        List<Map<String, Object>> modules = new ArrayList<>();
        for (String module : configSchemaValidator.normalizeModules(payload.modules(), false)) {
            Object raw = payload.data() == null ? null : payload.data().get(module);
            modules.add(configSchemaValidator.previewModule(module, raw, warnings));
        }
        Map<String, Object> summary = configSchemaValidator.inspectPayload(payload, SCHEMA_VERSION);
        return new ConfigImportPreview(payload.schemaVersion(), modules, warnings, summary);
    }

    public Map<String, Object> inspect(ConfigTransferPayload payload) {
        return configSchemaValidator.inspectPayload(payload, SCHEMA_VERSION);
    }

    @Transactional(rollbackOn = Exception.class)
    public Map<String, Object> importPayload(ConfigTransferPayload payload) {
        try {
            configSchemaValidator.validatePayload(payload, SCHEMA_VERSION);
            List<String> warnings = new ArrayList<>();
            List<Map<String, Object>> moduleResults = new ArrayList<>();
            for (String module : configSchemaValidator.normalizeModules(payload.modules(), false)) {
                moduleResults.add(configImportService.importModule(module, payload, warnings));
            }
            return Map.of(
                    "schemaVersion", payload.schemaVersion(),
                    "importedAt", Instant.now().toString(),
                    "modules", moduleResults,
                    "warnings", warnings
            );
        } catch (IllegalArgumentException e) {
            throw new ConfigTransferException(e.getMessage());
        }
    }

    public static class ConfigTransferException extends RuntimeException {
        public ConfigTransferException(String message) {
            super(message);
        }
    }
}
