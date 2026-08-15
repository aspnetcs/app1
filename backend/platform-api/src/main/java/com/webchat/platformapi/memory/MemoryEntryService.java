package com.webchat.platformapi.memory;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemoryEntryService {

    private final MemoryEntryRepository repository;

    public MemoryEntryService(MemoryEntryRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> listEntries(UUID userId, int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        return repository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, "active", PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toPayload)
                .toList();
    }

    @Transactional
    public void deleteEntry(UUID userId, UUID entryId) {
        MemoryEntryEntity entity = repository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("memory entry not found"));
        if (!userId.equals(entity.getUserId())) {
            throw new IllegalArgumentException("memory entry not found");
        }
        entity.setStatus("deleted");
        entity.setExpiresAt(Instant.now());
        repository.save(entity);
    }

    private Map<String, Object> toPayload(MemoryEntryEntity entity) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId().toString());
        payload.put("content", entity.getContent());
        payload.put("summary", entity.getSummary());
        payload.put("sourceType", entity.getSourceType());
        payload.put("status", entity.getStatus());
        payload.put("updatedAt", entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
        payload.put("createdAt", entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
        return payload;
    }
}
