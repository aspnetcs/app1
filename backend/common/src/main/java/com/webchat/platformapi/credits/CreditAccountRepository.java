package com.webchat.platformapi.credits;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditAccountRepository extends JpaRepository<CreditAccountEntity, Long> {

    Optional<CreditAccountEntity> findByUserId(UUID userId);

    Optional<CreditAccountEntity> findByGuestId(String guestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccountEntity a where a.id = :id")
    Optional<CreditAccountEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccountEntity a where a.userId = :userId")
    Optional<CreditAccountEntity> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccountEntity a where a.guestId = :guestId")
    Optional<CreditAccountEntity> findByGuestIdForUpdate(@Param("guestId") String guestId);
}
