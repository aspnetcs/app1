package com.webchat.platformapi.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "memory_entry",
        indexes = {
                @Index(name = "idx_memory_entry_user_status", columnList = "user_id, status"),
                @Index(name = "idx_memory_entry_user_updated", columnList = "user_id, updated_at")
        }
)
public class MemoryEntryEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "manual";

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "consent_snapshot", nullable = false)
    private boolean consentSnapshot = true;

    @Column(name = "last_recalled_at")
    private Instant lastRecalledAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadataJson = new LinkedHashMap<>();

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
        if (sourceType == null || sourceType.isBlank()) {
            sourceType = "manual";
        }
        if (status == null || status.isBlank()) {
            status = "active";
        }
        if (metadataJson == null) {
            metadataJson = new LinkedHashMap<>();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
        if (metadataJson == null) {
            metadataJson = new LinkedHashMap<>();
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isConsentSnapshot() {
        return consentSnapshot;
    }

    public void setConsentSnapshot(boolean consentSnapshot) {
        this.consentSnapshot = consentSnapshot;
    }

    public Instant getLastRecalledAt() {
        return lastRecalledAt;
    }

    public void setLastRecalledAt(Instant lastRecalledAt) {
        this.lastRecalledAt = lastRecalledAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Map<String, Object> getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(Map<String, Object> metadataJson) {
        this.metadataJson = metadataJson == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadataJson);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
