package com.webchat.platformapi.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "user_saved_asset",
        indexes = {
                @Index(name = "idx_user_saved_asset_user_type", columnList = "user_id, asset_type, enabled"),
                @Index(name = "idx_user_saved_asset_user_source", columnList = "user_id, source_id")
        }
)
public class UserSavedAssetEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 32)
    private MarketAssetType assetType;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_config_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extraConfigJson = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (extraConfigJson == null) {
            extraConfigJson = new LinkedHashMap<>();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
        if (extraConfigJson == null) {
            extraConfigJson = new LinkedHashMap<>();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public MarketAssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(MarketAssetType assetType) {
        this.assetType = assetType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Map<String, Object> getExtraConfigJson() {
        return extraConfigJson;
    }

    public void setExtraConfigJson(Map<String, Object> extraConfigJson) {
        this.extraConfigJson = extraConfigJson == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraConfigJson);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
