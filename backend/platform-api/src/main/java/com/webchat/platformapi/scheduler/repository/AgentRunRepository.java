package com.webchat.platformapi.scheduler.repository;

import com.webchat.platformapi.scheduler.entity.AgentRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, UUID> {

    Page<AgentRunEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}

