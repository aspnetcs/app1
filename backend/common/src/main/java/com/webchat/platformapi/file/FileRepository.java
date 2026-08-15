package com.webchat.platformapi.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileEntity, String> {

    Optional<FileEntity> findByIdAndCreatedByAndDeletedAtIsNull(String id, UUID createdBy);

    Optional<FileEntity> findFirstByCreatedByAndSha256AndSizeBytesAndDeletedAtIsNull(UUID createdBy, String sha256, long sizeBytes);

    @Query("""
            select f from FileEntity f
            where (:includeDeleted = true or f.deletedAt is null)
              and (
                :keyword is null
                or lower(f.id) like concat('%', lower(:keyword), '%')
                or lower(f.originalName) like concat('%', lower(:keyword), '%')
              )
              and (:kind is null or f.kind = :kind)
              and (:createdBy is null or f.createdBy = :createdBy)
            """)
    Page<FileEntity> adminSearch(
            @Param("keyword") String keyword,
            @Param("kind") String kind,
            @Param("createdBy") UUID createdBy,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable
    );

    @Query("update FileEntity f set f.deletedAt = :deletedAt where f.id = :id and f.deletedAt is null")
    @org.springframework.data.jpa.repository.Modifying
    int softDelete(@Param("id") String id, @Param("deletedAt") Instant deletedAt);
}

