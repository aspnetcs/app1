package com.webchat.platformapi.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_conversation_binding", indexes = {
        @Index(name = "idx_knowledge_binding_conversation", columnList = "user_id, conversation_id")
})
public class KnowledgeConversationBindingEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "base_id", nullable = false)
    private UUID baseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public UUID getBaseId() { return baseId; }
    public void setBaseId(UUID baseId) { this.baseId = baseId; }
    public Instant getCreatedAt() { return createdAt; }
}
