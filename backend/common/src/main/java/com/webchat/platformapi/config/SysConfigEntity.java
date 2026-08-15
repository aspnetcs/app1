package com.webchat.platformapi.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sys_config")
public class SysConfigEntity {

    @Id
    @Column(name = "config_key", length = 128)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public SysConfigEntity() {}

    public SysConfigEntity(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.updatedAt = Instant.now();
    }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
