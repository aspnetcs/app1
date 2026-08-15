package com.webchat.platformapi.codetools.repository;

import com.webchat.platformapi.codetools.entity.CodeToolsTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CodeToolsTaskRepository extends JpaRepository<CodeToolsTaskEntity, UUID> {

    Page<CodeToolsTaskEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}

