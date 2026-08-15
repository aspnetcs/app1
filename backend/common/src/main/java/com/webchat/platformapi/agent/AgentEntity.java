package com.webchat.platformapi.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Unified Agent entity -- replaces ConversationTemplate, Prompt, and AgentTemplate.
 * Each agent is a reusable AI persona with system prompt, model binding, and metadata.
 */
@Entity
@Table(name = "ai_agent")
public class AgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // -- identity --

    @Column(length = 150, nullable = false)
    private String name = "";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description = "";

    @Column(length = 512, nullable = false)
    private String avatar = "";

    @Column(length = 64, nullable = false)
    private String icon = "";

    @Column(length = 80, nullable = false)
    private String category = "general";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String tags = "";

    // -- AI config --

    @Column(name = "model_id", length = 150, nullable = false)
    private String modelId = "";

    @Column(name = "system_prompt", columnDefinition = "TEXT", nullable = false)
    private String systemPrompt = "";

    @Column(name = "first_message", columnDefinition = "TEXT", nullable = false)
    private String firstMessage = "";

    @Column(name = "context_messages_json", columnDefinition = "TEXT", nullable = false)
    private String contextMessagesJson = "[]";

    @Column(nullable = false)
    private double temperature = 1.0;

    @Column(name = "top_p", nullable = false)
    private double topP = 1.0;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 0;

    // -- marketplace --

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AgentScope scope = AgentScope.SYSTEM;

    @Column(length = 100, nullable = false)
    private String author = "";

    @Column(nullable = false)
    private boolean featured = false;

    @Column(name = "install_count", nullable = false)
    private int installCount = 0;

    @Column(name = "required_tools_json", columnDefinition = "TEXT", nullable = false)
    private String requiredToolsJson = "[]";

    // -- ownership --

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "source_agent_id")
    private UUID sourceAgentId;

    // -- status --

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (name == null) name = "";
        if (description == null) description = "";
        if (avatar == null) avatar = "";
        if (icon == null) icon = "";
        if (category == null || category.isBlank()) category = "general";
        if (tags == null) tags = "";
        if (modelId == null) modelId = "";
        if (systemPrompt == null) systemPrompt = "";
        if (firstMessage == null) firstMessage = "";
        if (contextMessagesJson == null) contextMessagesJson = "[]";
        if (author == null) author = "";
        if (requiredToolsJson == null) requiredToolsJson = "[]";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // -- getters / setters --

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getFirstMessage() { return firstMessage; }
    public void setFirstMessage(String firstMessage) { this.firstMessage = firstMessage; }

    public String getContextMessagesJson() { return contextMessagesJson; }
    public void setContextMessagesJson(String contextMessagesJson) { this.contextMessagesJson = contextMessagesJson; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public AgentScope getScope() { return scope; }
    public void setScope(AgentScope scope) { this.scope = scope; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public int getInstallCount() { return installCount; }
    public void setInstallCount(int installCount) { this.installCount = installCount; }

    public String getRequiredToolsJson() { return requiredToolsJson; }
    public void setRequiredToolsJson(String requiredToolsJson) { this.requiredToolsJson = requiredToolsJson; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getSourceAgentId() { return sourceAgentId; }
    public void setSourceAgentId(UUID sourceAgentId) { this.sourceAgentId = sourceAgentId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
