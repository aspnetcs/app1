package com.webchat.platformapi.credits;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditSettlementSnapshotRepository extends JpaRepository<CreditSettlementSnapshotEntity, Long> {

    Page<CreditSettlementSnapshotEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<CreditSettlementSnapshotEntity> findByGuestIdOrderByCreatedAtDesc(String guestId, Pageable pageable);

    List<CreditSettlementSnapshotEntity> findByRequestId(String requestId);
}
