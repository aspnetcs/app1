package com.webchat.platformapi.config;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Simple service for reading/writing sys_config key-value pairs.
 * Used to persist runtime configuration changes across restarts.
 */
@Service
public class SysConfigService {

    private final SysConfigRepository repository;

    public SysConfigService(SysConfigRepository repository) {
        this.repository = repository;
    }

    public Optional<String> get(String key) {
        return repository.findById(key).map(SysConfigEntity::getConfigValue);
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public void set(String key, String value) {
        SysConfigEntity entity = repository.findById(key).orElse(null);
        if (entity == null) {
            entity = new SysConfigEntity(key, value);
        } else {
            entity.setConfigValue(value);
            entity.setUpdatedAt(Instant.now());
        }
        repository.save(entity);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return get(key).map(v -> {
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; }
        }).orElse(defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return get(key).map(v -> {
            try { return Long.parseLong(v); } catch (NumberFormatException e) { return defaultValue; }
        }).orElse(defaultValue);
    }
}
