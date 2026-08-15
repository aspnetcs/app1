package com.webchat.platformapi.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, UUID> {
    List<KnowledgeChunkEntity> findByBaseIdAndDeletedAtIsNullOrderByDocumentIdAscChunkNoAsc(UUID baseId);

    @Query(value = """
            select
                kc.id as id,
                kc.base_id as baseId,
                kc.document_id as documentId,
                kc.chunk_no as chunkNo,
                kc.chunk_text as chunkText,
                kc.metadata_json as metadataJson,
                1 - (kc.embedding <=> cast(:embedding as vector)) as similarity
            from knowledge_chunk kc
            where kc.base_id = :baseId and kc.deleted_at is null
            order by kc.embedding <=> cast(:embedding as vector) asc
            limit :limit
            """, nativeQuery = true)
    List<KnowledgeChunkSearchRow> searchByBaseId(@Param("baseId") UUID baseId,
                                                 @Param("embedding") String embedding,
                                                 @Param("limit") int limit);
}
