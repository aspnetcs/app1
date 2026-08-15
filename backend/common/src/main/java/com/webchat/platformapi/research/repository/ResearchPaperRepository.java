package com.webchat.platformapi.research.repository;

import com.webchat.platformapi.research.entity.ResearchPaperEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchPaperRepository extends JpaRepository<ResearchPaperEntity, UUID> {

    List<ResearchPaperEntity> findByRunIdOrderByIterationDesc(UUID runId);

    Optional<ResearchPaperEntity> findFirstByRunIdOrderByIterationDesc(UUID runId);
}
