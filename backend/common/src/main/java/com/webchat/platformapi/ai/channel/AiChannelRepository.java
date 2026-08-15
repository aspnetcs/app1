package com.webchat.platformapi.ai.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiChannelRepository extends JpaRepository<AiChannelEntity, Long> {

    Optional<AiChannelEntity> findFirstByNameAndTypeAndBaseUrl(String name, String type, String baseUrl);

    @Query(value = """
            SELECT *
            FROM ai_channel
            WHERE enabled = TRUE
              AND status = 1
              AND (
                :model IS NULL OR :model = ''
                OR models IS NULL OR btrim(models) = ''
                OR :model = ANY(string_to_array(replace(models, ' ', ''), ','))
              )
            """, nativeQuery = true)
    List<AiChannelEntity> findActiveByModel(@Param("model") String model);

    List<AiChannelEntity> findByEnabledTrue();

    List<AiChannelEntity> findByEnabledTrueAndStatus(int status);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ai_channel
            SET success_count = success_count + 1,
                consecutive_failures = 0,
                last_success_at = NOW(),
                status = CASE WHEN status = 3 THEN 1 ELSE status END,
                updated_at = NOW()
            WHERE id = :id
            """, nativeQuery = true)
    int markSuccess(@Param("id") long id);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ai_channel
            SET fail_count = fail_count + 1,
                consecutive_failures = consecutive_failures + 1,
                last_fail_at = NOW(),
                status = CASE
                    WHEN status = 1 AND (consecutive_failures + 1) >= :disableAfter THEN 3
                    ELSE status
                END,
                updated_at = NOW()
            WHERE id = :id
            """, nativeQuery = true)
    int markFailure(@Param("id") long id, @Param("disableAfter") int disableAfter);

    @Query(value = """
            SELECT *
            FROM ai_channel
            WHERE enabled = TRUE
              AND status = 3
              AND (last_fail_at IS NULL OR last_fail_at < :cutoff)
            ORDER BY last_fail_at NULLS FIRST
            LIMIT :limit
            """, nativeQuery = true)
    List<AiChannelEntity> findAutoDisabledReadyToProbe(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
