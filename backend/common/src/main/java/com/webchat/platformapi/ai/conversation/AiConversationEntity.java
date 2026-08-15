package com.webchat.platformapi.ai.conversation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversation")
public class AiConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String title;

    private String model;

    @Column(name = "compare_models_json", columnDefinition = "TEXT", nullable = false)
    private String compareModelsJson = "[]";

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode = "chat";

    @Column(name = "captain_selection_mode", length = 32)
    private String captainSelectionMode;

    @Column(name = "team_turns_json", columnDefinition = "TEXT")
    private String teamTurnsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "source_conversation_id")
    private UUID sourceConversationId;

    @Column(name = "source_message_id")
    private UUID sourceMessageId;

    @Column(name = "is_temporary", nullable = false)
    private boolean temporary;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Column(nullable = false)
    private boolean starred;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getCompareModelsJson() { return compareModelsJson; }
    public void setCompareModelsJson(String compareModelsJson) { this.compareModelsJson = compareModelsJson; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getCaptainSelectionMode() { return captainSelectionMode; }
    public void setCaptainSelectionMode(String captainSelectionMode) { this.captainSelectionMode = captainSelectionMode; }

    public String getTeamTurnsJson() { return teamTurnsJson; }
    public void setTeamTurnsJson(String teamTurnsJson) { this.teamTurnsJson = teamTurnsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public UUID getSourceConversationId() { return sourceConversationId; }
    public void setSourceConversationId(UUID sourceConversationId) { this.sourceConversationId = sourceConversationId; }

    public UUID getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(UUID sourceMessageId) { this.sourceMessageId = sourceMessageId; }

    public boolean isTemporary() { return temporary; }
    public void setTemporary(boolean temporary) { this.temporary = temporary; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public Instant getPinnedAt() { return pinnedAt; }
    public void setPinnedAt(Instant pinnedAt) { this.pinnedAt = pinnedAt; }

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
}
