package com.webchat.platformapi.research.repository;

import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResearchProjectRepository extends JpaRepository<ResearchProjectEntity, UUID> {

    List<ResearchProjectEntity> findByUserIdAndStatusNotOrderByCreatedAtDesc(UUID userId, String excludeStatus);

    List<ResearchProjectEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndStatusIn(UUID userId, List<String> statuses);
}
