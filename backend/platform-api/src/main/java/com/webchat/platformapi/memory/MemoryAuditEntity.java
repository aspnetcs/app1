package com.webchat.platformapi.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "memory_audit",
        indexes = {
                @Index(name = "idx_memory_audit_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_memory_audit_action_created", columnList = "action, created_at")
        }
)
public class MemoryAuditEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(nullable = false, length = 20)
    private String status = "success";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> detailJson = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null || status.isBlank()) {
            status = "success";
        }
        if (detailJson == null) {
            detailJson = new LinkedHashMap<>();
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, Object> getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(Map<String, Object> detailJson) {
        this.detailJson = detailJson == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detailJson);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
