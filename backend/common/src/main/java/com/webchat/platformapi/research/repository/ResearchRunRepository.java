package com.webchat.platformapi.research.repository;

import com.webchat.platformapi.research.entity.ResearchRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchRunRepository extends JpaRepository<ResearchRunEntity, UUID> {

    List<ResearchRunEntity> findByProjectIdOrderByRunNumberDesc(UUID projectId);

    List<ResearchRunEntity> findByStatusOrderByStartedAtAsc(String status);

    Optional<ResearchRunEntity> findFirstByProjectIdAndStatusOrderByRunNumberDesc(UUID projectId, String status);

    Optional<ResearchRunEntity> findFirstByProjectIdOrderByRunNumberDesc(UUID projectId);

    int countByProjectId(UUID projectId);
}
