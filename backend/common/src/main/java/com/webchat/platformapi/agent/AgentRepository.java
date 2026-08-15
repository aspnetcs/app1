package com.webchat.platformapi.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID>,
        JpaSpecificationExecutor<AgentEntity> {

    Optional<AgentEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<AgentEntity> findByScopeAndEnabledTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(AgentScope scope);

    @Query("""
            SELECT a
            FROM AgentEntity a
            WHERE a.scope = :scope
              AND a.enabled = true
              AND a.deletedAt IS NULL
              AND LOWER(TRIM(a.category)) = :normalizedCategory
            ORDER BY a.sortOrder ASC, a.createdAt DESC
            """)
    List<AgentEntity> findByScopeAndEnabledTrueAndDeletedAtIsNullAndNormalizedCategoryOrderBySortOrderAscCreatedAtDesc(
            @Param("scope") AgentScope scope,
            @Param("normalizedCategory") String normalizedCategory
    );

    List<AgentEntity> findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE AgentEntity a SET a.installCount = a.installCount + 1 WHERE a.id = :id")
    int incrementInstallCount(@Param("id") UUID id);
}
