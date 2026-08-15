package com.webchat.platformapi.ai.extension;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {
    List<McpServerEntity> findByEnabledTrue();
}
