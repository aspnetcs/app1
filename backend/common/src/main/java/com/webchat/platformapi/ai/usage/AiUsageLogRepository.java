package com.webchat.platformapi.ai.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLogEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiUsageLogEntity u
               set u.userId = :targetUserId
             where u.userId = :sourceUserId
            """)
    int reassignUserId(
            @Param("sourceUserId") UUID sourceUserId,
            @Param("targetUserId") UUID targetUserId
    );

    @Query("""
            SELECT new map(
                COALESCE(SUM(u.promptTokens), 0L) as promptTokens,
                COALESCE(SUM(u.completionTokens), 0L) as completionTokens,
                COALESCE(SUM(u.totalTokens), 0L) as totalTokens,
                COUNT(u) as requestCount
            )
            FROM AiUsageLogEntity u
            WHERE u.userId = :userId AND u.createdAt >= :from AND u.createdAt <= :to
            """)
    Map<String, Object> summarizeByUser(@Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT new map(
                u.channelId as channelId,
                u.model as model,
                COALESCE(SUM(u.totalTokens), 0L) as totalTokens,
                COUNT(u) as requestCount
            )
            FROM AiUsageLogEntity u
            WHERE u.createdAt >= :from AND u.createdAt <= :to
            GROUP BY u.channelId, u.model
            ORDER BY SUM(u.totalTokens) DESC
            """)
    List<Map<String, Object>> summarizeByChannelAndModel(@Param("from") Instant from, @Param("to") Instant to);
}
