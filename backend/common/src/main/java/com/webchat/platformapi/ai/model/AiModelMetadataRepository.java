package com.webchat.platformapi.ai.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiModelMetadataRepository extends JpaRepository<AiModelMetadataEntity, Long> {

    Optional<AiModelMetadataEntity> findByModelId(String modelId);

    Optional<AiModelMetadataEntity> findByDefaultSelectedTrue();

    void deleteByModelId(String modelId);

    List<AiModelMetadataEntity> findAllByModelIdIn(Collection<String> modelIds);
}
