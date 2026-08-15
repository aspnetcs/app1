package com.webchat.platformapi.research.repository;

import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchStageLogRepository extends JpaRepository<ResearchStageLogEntity, UUID> {

    List<ResearchStageLogEntity> findByRunIdOrderByStageNumberAscCreatedAtAsc(UUID runId);

    List<ResearchStageLogEntity> findByRunIdAndStageNumberAndStatusOrderByCreatedAtAsc(UUID runId, int stageNumber, String status);

    Optional<ResearchStageLogEntity> findFirstByRunIdAndStageNumberOrderByCreatedAtDesc(UUID runId, int stageNumber);
}
