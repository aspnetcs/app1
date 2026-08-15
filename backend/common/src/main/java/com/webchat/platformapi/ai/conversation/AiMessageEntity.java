package com.webchat.platformapi.ai.conversation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_message")
public class AiMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType = "text";

    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(length = 100)
    private String model;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "parent_message_id")
    private UUID parentMessageId;

    @Column(name = "multi_round_id")
    private UUID multiRoundId;

    @Column(name = "branch_index", nullable = false)
    private Integer branchIndex = 0;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (contentType == null) contentType = "text";
        if (version == null) version = 1;
        if (branchIndex == null) branchIndex = 0;
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public UUID getParentMessageId() { return parentMessageId; }
    public void setParentMessageId(UUID parentMessageId) { this.parentMessageId = parentMessageId; }

    public UUID getMultiRoundId() { return multiRoundId; }
    public void setMultiRoundId(UUID multiRoundId) { this.multiRoundId = multiRoundId; }

    public Integer getBranchIndex() { return branchIndex; }
    public void setBranchIndex(Integer branchIndex) { this.branchIndex = branchIndex; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
}
