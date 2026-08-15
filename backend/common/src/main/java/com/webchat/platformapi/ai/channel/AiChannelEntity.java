package com.webchat.platformapi.ai.channel;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "ai_channel")
public class AiChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column
    private String models;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_mapping", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> modelMapping = new HashMap<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int priority = 0;

    @Column(nullable = false)
    private int weight = 1;

    @Column(name = "max_concurrent", nullable = false)
    private int maxConcurrent = 4;

    @Column(nullable = false)
    private int status = AiChannelStatus.NORMAL;

    @Column(name = "test_model")
    private String testModel;

    @Column(name = "fallback_channel_id")
    private Long fallbackChannelId;

    @Column(name = "success_count", nullable = false)
    private long successCount = 0;

    @Column(name = "fail_count", nullable = false)
    private long failCount = 0;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_fail_at")
    private Instant lastFailAt;

    @Column
    private java.math.BigDecimal balance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extraConfig = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (name == null) name = "";
        if (type == null) type = "";
        if (baseUrl == null) baseUrl = "";
        if (modelMapping == null) modelMapping = new HashMap<>();
        if (extraConfig == null) extraConfig = new HashMap<>();
        if (weight <= 0) weight = 1;
        if (maxConcurrent <= 0) maxConcurrent = 1;
        if (status <= 0) status = AiChannelStatus.NORMAL;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
        if (modelMapping == null) modelMapping = new HashMap<>();
        if (extraConfig == null) extraConfig = new HashMap<>();
        if (weight <= 0) weight = 1;
        if (maxConcurrent <= 0) maxConcurrent = 1;
        if (status <= 0) status = AiChannelStatus.NORMAL;
    }

    public String getMappedModel(String requestedModel) {
        if (requestedModel == null) return null;
        Map<String, String> mm = modelMapping;
        if (mm != null && !mm.isEmpty()) {
            // 1) Exact match
            String mapped = mm.get(requestedModel);
            if (mapped != null && !mapped.isBlank()) return mapped.trim();

            // 2) Wildcard match: "gpt-4*" matches "gpt-4o", "gpt-4-turbo", etc.
            for (Map.Entry<String, String> entry : mm.entrySet()) {
                String pattern = entry.getKey();
                if (pattern != null && pattern.endsWith("*")) {
                    String prefix = pattern.substring(0, pattern.length() - 1);
                    if (requestedModel.startsWith(prefix)) {
                        String val = entry.getValue();
                        if (val != null && !val.isBlank()) return val.trim();
                    }
                }
            }
        }

        // 3) Fallback model from extraConfig
        Map<String, Object> extra = extraConfig;
        if (extra != null) {
            Object fb = extra.get("fallback_model");
            if (fb != null) {
                String fallback = String.valueOf(fb).trim();
                if (!fallback.isEmpty()) return fallback;
            }
        }

        return requestedModel;
    }

    // ==================== getters/setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public Map<String, String> getModelMapping() {
        return modelMapping;
    }

    public void setModelMapping(Map<String, String> modelMapping) {
        this.modelMapping = modelMapping;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTestModel() {
        return testModel;
    }

    public void setTestModel(String testModel) {
        this.testModel = testModel;
    }

    public Long getFallbackChannelId() {
        return fallbackChannelId;
    }

    public void setFallbackChannelId(Long fallbackChannelId) {
        this.fallbackChannelId = fallbackChannelId;
    }

    public Map<String, Object> getExtraConfig() {
        return extraConfig;
    }

    public void setExtraConfig(Map<String, Object> extraConfig) {
        this.extraConfig = extraConfig;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailCount() {
        return failCount;
    }

    public void setFailCount(long failCount) {
        this.failCount = failCount;
    }

    public void incrementSuccessCount() {
        this.successCount++;
        this.consecutiveFailures = 0;
        this.lastSuccessAt = Instant.now();
    }

    public void incrementFailCount() {
        this.failCount++;
        this.consecutiveFailures++;
        this.lastFailAt = Instant.now();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public Instant getLastFailAt() {
        return lastFailAt;
    }

    public void setLastFailAt(Instant lastFailAt) {
        this.lastFailAt = lastFailAt;
    }

    public java.math.BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(java.math.BigDecimal balance) {
        this.balance = balance;
    }
}

