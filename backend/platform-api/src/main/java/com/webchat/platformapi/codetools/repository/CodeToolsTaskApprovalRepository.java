package com.webchat.platformapi.codetools.repository;

import com.webchat.platformapi.codetools.entity.CodeToolsTaskApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeToolsTaskApprovalRepository extends JpaRepository<CodeToolsTaskApprovalEntity, UUID> {

    Optional<CodeToolsTaskApprovalEntity> findByTaskId(UUID taskId);

    List<CodeToolsTaskApprovalEntity> findByTaskIdIn(Collection<UUID> taskIds);
}

