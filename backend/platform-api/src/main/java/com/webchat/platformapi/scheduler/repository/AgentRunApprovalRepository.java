package com.webchat.platformapi.scheduler.repository;

import com.webchat.platformapi.scheduler.entity.AgentRunApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgentRunApprovalRepository extends JpaRepository<AgentRunApprovalEntity, UUID> {

    Optional<AgentRunApprovalEntity> findByRunId(UUID runId);
}

