package com.webchat.platformapi.ai.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiMessageBlockRepository extends JpaRepository<AiMessageBlockEntity, UUID> {

    List<AiMessageBlockEntity> findByMessageIdOrderBySequenceNoAscCreatedAtAsc(UUID messageId);

    List<AiMessageBlockEntity> findByMessageIdInOrderByMessageIdAscSequenceNoAscCreatedAtAsc(Collection<UUID> messageIds);

    void deleteByMessageId(UUID messageId);
}
