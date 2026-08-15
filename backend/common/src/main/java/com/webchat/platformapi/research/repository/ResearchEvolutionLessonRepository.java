package com.webchat.platformapi.research.repository;

import com.webchat.platformapi.research.entity.ResearchEvolutionLessonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResearchEvolutionLessonRepository extends JpaRepository<ResearchEvolutionLessonEntity, UUID> {

    List<ResearchEvolutionLessonEntity> findByProjectIdAndStageNameOrderByCreatedAtDesc(
        UUID projectId, String stageName);

    List<ResearchEvolutionLessonEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);
}
