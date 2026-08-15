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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "market_catalog_item",
        indexes = {
                @Index(name = "idx_market_catalog_type_enabled", columnList = "asset_type, enabled, sort_order"),
                @Index(name = "idx_market_catalog_featured", columnList = "featured, sort_order")
        }
)
public class MarketCatalogItemEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 32)
    private MarketAssetType assetType;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Column(nullable = false, length = 180)
    private String title = "";

    @Column(length = 320)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 80)
    private String category;

    @Column(length = 500)
    private String tags = "";

    @Column(length = 500)
    private String cover = "";

    @Column(nullable = false)
    private boolean featured = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

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
        if (title == null) {
            title = "";
        }
        if (tags == null) {
            tags = "";
        }
        if (cover == null) {
            cover = "";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
        if (title == null) {
            title = "";
        }
        if (tags == null) {
            tags = "";
        }
        if (cover == null) {
            cover = "";
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
