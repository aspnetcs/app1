package com.webchat.platformapi.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntryEntity, UUID> {

    long countByUserIdAndStatus(UUID userId, String status);

    List<MemoryEntryEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, String status, Pageable pageable);
}
