package com.webchat.platformapi.codetools.repository;

import com.webchat.platformapi.codetools.entity.CodeToolsTaskArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CodeToolsTaskArtifactRepository extends JpaRepository<CodeToolsTaskArtifactEntity, Long> {

    List<CodeToolsTaskArtifactEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}

