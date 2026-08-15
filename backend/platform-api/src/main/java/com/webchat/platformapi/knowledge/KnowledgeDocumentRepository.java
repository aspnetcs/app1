package com.webchat.platformapi.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, UUID> {
    List<KnowledgeDocumentEntity> findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID baseId);
}
