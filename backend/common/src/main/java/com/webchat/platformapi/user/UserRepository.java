package com.webchat.platformapi.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    /** Soft-delete aware lookup. */
    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<UserEntity> findByPhoneAndDeletedAtIsNull(String phone);

    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);

    /** Atomically increment token_used. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserEntity u SET u.tokenUsed = u.tokenUsed + :amount WHERE u.id = :userId")
    int incrementTokenUsed(@Param("userId") UUID userId, @Param("amount") long amount);

    /** Atomically increment token_used only when quota remains. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserEntity u
               SET u.tokenUsed = u.tokenUsed + :amount
             WHERE u.id = :userId
               AND (u.tokenQuota = 0 OR u.tokenUsed + :amount <= u.tokenQuota)
            """)
    int incrementTokenUsedIfWithinQuota(@Param("userId") UUID userId, @Param("amount") long amount);

    /** Atomically decrement token_used without going negative. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserEntity u
               SET u.tokenUsed = u.tokenUsed - :amount
             WHERE u.id = :userId
               AND u.tokenUsed >= :amount
            """)
    int decrementTokenUsed(@Param("userId") UUID userId, @Param("amount") long amount);
}

