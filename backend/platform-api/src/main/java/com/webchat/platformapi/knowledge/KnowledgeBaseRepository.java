package com.webchat.platformapi.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, UUID> {
    List<KnowledgeBaseEntity> findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID ownerUserId);
    Optional<KnowledgeBaseEntity> findByIdAndOwnerUserIdAndDeletedAtIsNull(UUID id, UUID ownerUserId);
}
