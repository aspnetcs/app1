package com.webchat.platformapi.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSessionEntity, UUID> {
    Optional<DeviceSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    /** 查找用户所有活跃会话（用于账户删除时批量吊销） */
    List<DeviceSessionEntity> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * 单次查询同时校验：
     * - session 属于 user
     * - session 未撤销/未过期
     * - user 未软删除
     */
    @Query("""
            select s from DeviceSessionEntity s, com.webchat.platformapi.user.UserEntity u
            where s.id = :sessionId
              and s.userId = :userId
              and s.revokedAt is null
              and (s.expiresAt is null or s.expiresAt > :now)
              and u.id = s.userId
              and u.deletedAt is null
            """)
    Optional<DeviceSessionEntity> findActiveSessionForUser(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );
}


