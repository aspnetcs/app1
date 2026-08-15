package com.webchat.platformapi.memory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemoryAuditRepository extends JpaRepository<MemoryAuditEntity, UUID> {

    Page<MemoryAuditEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);
}
