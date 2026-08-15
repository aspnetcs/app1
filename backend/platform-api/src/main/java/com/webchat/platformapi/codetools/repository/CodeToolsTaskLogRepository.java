package com.webchat.platformapi.codetools.repository;

import com.webchat.platformapi.codetools.entity.CodeToolsTaskLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CodeToolsTaskLogRepository extends JpaRepository<CodeToolsTaskLogEntity, Long> {

    List<CodeToolsTaskLogEntity> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}

