package com.webchat.platformapi.ai.conversation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversationEntity, UUID> {

    List<AiConversationEntity> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);

    List<AiConversationEntity> findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(UUID userId);

    List<AiConversationEntity> findByUserIdAndDeletedAtIsNotNullAndTemporaryFalseOrderByDeletedAtDesc(UUID userId);

    Page<AiConversationEntity> findByUserIdAndDeletedAtIsNullAndTemporaryFalseOrderByPinnedDescUpdatedAtDesc(UUID userId, Pageable pageable);

    List<AiConversationEntity> findByPinnedTrueAndPinnedAtIsNullAndDeletedAtIsNullAndTemporaryFalse();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiConversationEntity c
               set c.userId = :targetUserId
             where c.userId = :sourceUserId
               and c.deletedAt is null
            """)
    int reassignUserId(
            @Param("sourceUserId") UUID sourceUserId,
            @Param("targetUserId") UUID targetUserId
    );
}
