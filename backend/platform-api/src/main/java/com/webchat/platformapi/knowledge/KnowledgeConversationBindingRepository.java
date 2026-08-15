package com.webchat.platformapi.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeConversationBindingRepository extends JpaRepository<KnowledgeConversationBindingEntity, UUID> {
    Optional<KnowledgeConversationBindingEntity> findByUserIdAndConversationIdAndBaseId(UUID userId, UUID conversationId, UUID baseId);
    List<KnowledgeConversationBindingEntity> findByUserIdAndConversationId(UUID userId, UUID conversationId);
    void deleteByUserIdAndConversationIdAndBaseId(UUID userId, UUID conversationId, UUID baseId);
}
