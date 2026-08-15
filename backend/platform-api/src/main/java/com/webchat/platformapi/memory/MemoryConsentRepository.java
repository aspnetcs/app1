package com.webchat.platformapi.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemoryConsentRepository extends JpaRepository<MemoryConsentEntity, UUID> {
}
