package com.webchat.platformapi.credits;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_accounts")
public class CreditAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_id", length = 64)
    private String guestId;

    @Column(name = "role_snapshot", length = 20, nullable = false)
    private String roleSnapshot = "user";

    @Column(name = "credit_balance", nullable = false)
    private long creditBalance = 0;

    @Column(name = "credit_used", nullable = false)
    private long creditUsed = 0;

    @Column(name = "manual_credit_adjustment", nullable = false)
    private long manualCreditAdjustment = 0;

    @Column(name = "period_credits", nullable = false)
    private long periodCredits = 0;

    @Column(name = "period_type", length = 20, nullable = false)
    private String periodType = "monthly";

    @Column(name = "period_start_at")
    private Instant periodStartAt;

    @Column(name = "period_end_at")
    private Instant periodEndAt;

    @Column(name = "last_reset_at")
    private Instant lastResetAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }

    public String getRoleSnapshot() { return roleSnapshot; }
    public void setRoleSnapshot(String roleSnapshot) { this.roleSnapshot = roleSnapshot; }

    public long getCreditBalance() { return creditBalance; }
    public void setCreditBalance(long creditBalance) { this.creditBalance = creditBalance; }

    public long getCreditUsed() { return creditUsed; }
    public void setCreditUsed(long creditUsed) { this.creditUsed = creditUsed; }

    public long getManualCreditAdjustment() { return manualCreditAdjustment; }
    public void setManualCreditAdjustment(long manualCreditAdjustment) { this.manualCreditAdjustment = manualCreditAdjustment; }

    public long getPeriodCredits() { return periodCredits; }
    public void setPeriodCredits(long periodCredits) { this.periodCredits = periodCredits; }

    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }

    public Instant getPeriodStartAt() { return periodStartAt; }
    public void setPeriodStartAt(Instant periodStartAt) { this.periodStartAt = periodStartAt; }

    public Instant getPeriodEndAt() { return periodEndAt; }
    public void setPeriodEndAt(Instant periodEndAt) { this.periodEndAt = periodEndAt; }

    public Instant getLastResetAt() { return lastResetAt; }
    public void setLastResetAt(Instant lastResetAt) { this.lastResetAt = lastResetAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isUnlimitedPeriodCredits() {
        return periodCredits < 0;
    }

    public long getRemainingPeriodicCredits() {
        if (isUnlimitedPeriodCredits()) {
            return Long.MAX_VALUE / 4;
        }
        long periodicCredits = Math.max(0L, periodCredits);
        long usedCredits = Math.max(0L, creditUsed);
        return Math.max(0L, periodicCredits - usedCredits);
    }

    public long getEffectiveBalance() {
        if (isUnlimitedPeriodCredits()) {
            return Long.MAX_VALUE / 4;
        }
        long durableBalance = creditBalance;
        long remainingPeriodicCredits = getRemainingPeriodicCredits();
        long total = durableBalance + remainingPeriodicCredits;
        return Math.max(0L, total);
    }
}
