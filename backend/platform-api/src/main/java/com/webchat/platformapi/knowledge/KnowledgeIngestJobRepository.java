package com.webchat.platformapi.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeIngestJobRepository extends JpaRepository<KnowledgeIngestJobEntity, UUID> {
    Optional<KnowledgeIngestJobEntity> findByIdAndBaseId(UUID id, UUID baseId);
    List<KnowledgeIngestJobEntity> findTop50ByBaseIdOrderByCreatedAtDesc(UUID baseId);
    List<KnowledgeIngestJobEntity> findTop100ByOrderByCreatedAtDesc();
}
