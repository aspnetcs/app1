package com.webchat.platformapi.ai.channel;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ai_channel_key")
public class AiChannelKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private AiChannelEntity channel;

    @Column(name = "channel_id", insertable = false, updatable = false)
    private Long channelId;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "api_key_encrypted", nullable = false)
    private String apiKeyEncrypted;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int status = AiChannelStatus.NORMAL;

    @Column(nullable = false)
    private int weight = 1;

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (keyHash == null) keyHash = "";
        if (apiKeyEncrypted == null) apiKeyEncrypted = "";
        if (weight <= 0) weight = 1;
        if (status <= 0) status = AiChannelStatus.NORMAL;
        if (consecutiveFailures < 0) consecutiveFailures = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
        if (weight <= 0) weight = 1;
        if (status <= 0) status = AiChannelStatus.NORMAL;
        if (consecutiveFailures < 0) consecutiveFailures = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AiChannelEntity getChannel() {
        return channel;
    }

    public void setChannel(AiChannelEntity channel) {
        this.channel = channel;
    }

    public Long getChannelId() {
        return channelId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public void setApiKeyEncrypted(String apiKeyEncrypted) {
        this.apiKeyEncrypted = apiKeyEncrypted;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
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
}
