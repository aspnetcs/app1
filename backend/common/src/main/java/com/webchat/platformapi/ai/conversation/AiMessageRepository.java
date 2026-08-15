package com.webchat.platformapi.ai.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessageEntity, UUID> {

    interface ConversationMessageCount {
        UUID getConversationId();
        long getMessageCount();
    }

    List<AiMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<AiMessageEntity> findByConversationIdAndParentMessageIdIsNullOrderByCreatedAtAsc(UUID conversationId);

    List<AiMessageEntity> findByParentMessageIdOrderByVersionAsc(UUID parentMessageId);

    Optional<AiMessageEntity> findByIdAndConversationId(UUID id, UUID conversationId);

    long countByConversationIdAndParentMessageIdIsNull(UUID conversationId);

    long countByConversationIdAndMultiRoundId(UUID conversationId, UUID multiRoundId);

    @Query("""
            select m.conversationId as conversationId, count(m.id) as messageCount
            from AiMessageEntity m
            where m.conversationId in :conversationIds
              and m.parentMessageId is null
            group by m.conversationId
            """)
    List<ConversationMessageCount> countByConversationIds(@Param("conversationIds") Collection<UUID> conversationIds);
}
