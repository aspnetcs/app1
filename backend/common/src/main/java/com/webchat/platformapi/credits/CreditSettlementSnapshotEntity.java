package com.webchat.platformapi.credits;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_settlement_snapshots")
public class CreditSettlementSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_id", length = 64)
    private String guestId;

    @Column(name = "role_snapshot", length = 20)
    private String roleSnapshot;

    @Column(name = "model_id", length = 150)
    private String modelId;

    @Column(name = "model_name_snapshot", length = 150)
    private String modelNameSnapshot;

    @Column(name = "free_mode_snapshot", nullable = false)
    private boolean freeModeSnapshot;

    @Column(name = "billing_enabled_snapshot", nullable = false)
    private boolean billingEnabledSnapshot;

    @Column(name = "credits_per_usd_snapshot", nullable = false)
    private int creditsPerUsdSnapshot;

    @Column(name = "request_price_usd_snapshot", precision = 12, scale = 6)
    private BigDecimal requestPriceUsdSnapshot;

    @Column(name = "prompt_price_usd_snapshot", precision = 12, scale = 6)
    private BigDecimal promptPriceUsdSnapshot;

    @Column(name = "input_price_usd_per_1m_snapshot", precision = 12, scale = 6)
    private BigDecimal inputPriceUsdPer1MSnapshot;

    @Column(name = "output_price_usd_per_1m_snapshot", precision = 12, scale = 6)
    private BigDecimal outputPriceUsdPer1MSnapshot;

    @Column(name = "request_count", nullable = false)
    private int requestCount = 0;

    @Column(name = "prompt_count", nullable = false)
    private int promptCount = 0;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens = 0;

    @Column(name = "request_cost_usd", precision = 12, scale = 6)
    private BigDecimal requestCostUsd;

    @Column(name = "prompt_cost_usd", precision = 12, scale = 6)
    private BigDecimal promptCostUsd;

    @Column(name = "input_cost_usd", precision = 12, scale = 6)
    private BigDecimal inputCostUsd;

    @Column(name = "output_cost_usd", precision = 12, scale = 6)
    private BigDecimal outputCostUsd;

    @Column(name = "reserved_credits", nullable = false)
    private long reservedCredits = 0;

    @Column(name = "reserved_period_credits", nullable = false)
    private long reservedPeriodCredits = 0;

    @Column(name = "reserved_balance_credits", nullable = false)
    private long reservedBalanceCredits = 0;

    @Column(name = "settled_credits", nullable = false)
    private long settledCredits = 0;

    @Column(name = "refunded_credits", nullable = false)
    private long refundedCredits = 0;

    @Column(name = "settlement_status", length = 20, nullable = false)
    private String settlementStatus = "pending";

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }

    public String getRoleSnapshot() { return roleSnapshot; }
    public void setRoleSnapshot(String roleSnapshot) { this.roleSnapshot = roleSnapshot; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getModelNameSnapshot() { return modelNameSnapshot; }
    public void setModelNameSnapshot(String modelNameSnapshot) { this.modelNameSnapshot = modelNameSnapshot; }

    public boolean isFreeModeSnapshot() { return freeModeSnapshot; }
    public void setFreeModeSnapshot(boolean freeModeSnapshot) { this.freeModeSnapshot = freeModeSnapshot; }

    public boolean isBillingEnabledSnapshot() { return billingEnabledSnapshot; }
    public void setBillingEnabledSnapshot(boolean billingEnabledSnapshot) { this.billingEnabledSnapshot = billingEnabledSnapshot; }

    public int getCreditsPerUsdSnapshot() { return creditsPerUsdSnapshot; }
    public void setCreditsPerUsdSnapshot(int creditsPerUsdSnapshot) { this.creditsPerUsdSnapshot = creditsPerUsdSnapshot; }

    public BigDecimal getRequestPriceUsdSnapshot() { return requestPriceUsdSnapshot; }
    public void setRequestPriceUsdSnapshot(BigDecimal requestPriceUsdSnapshot) { this.requestPriceUsdSnapshot = requestPriceUsdSnapshot; }

    public BigDecimal getPromptPriceUsdSnapshot() { return promptPriceUsdSnapshot; }
    public void setPromptPriceUsdSnapshot(BigDecimal promptPriceUsdSnapshot) { this.promptPriceUsdSnapshot = promptPriceUsdSnapshot; }

    public BigDecimal getInputPriceUsdPer1MSnapshot() { return inputPriceUsdPer1MSnapshot; }
    public void setInputPriceUsdPer1MSnapshot(BigDecimal inputPriceUsdPer1MSnapshot) { this.inputPriceUsdPer1MSnapshot = inputPriceUsdPer1MSnapshot; }

    public BigDecimal getOutputPriceUsdPer1MSnapshot() { return outputPriceUsdPer1MSnapshot; }
    public void setOutputPriceUsdPer1MSnapshot(BigDecimal outputPriceUsdPer1MSnapshot) { this.outputPriceUsdPer1MSnapshot = outputPriceUsdPer1MSnapshot; }

    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }

    public int getPromptCount() { return promptCount; }
    public void setPromptCount(int promptCount) { this.promptCount = promptCount; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public BigDecimal getRequestCostUsd() { return requestCostUsd; }
    public void setRequestCostUsd(BigDecimal requestCostUsd) { this.requestCostUsd = requestCostUsd; }

    public BigDecimal getPromptCostUsd() { return promptCostUsd; }
    public void setPromptCostUsd(BigDecimal promptCostUsd) { this.promptCostUsd = promptCostUsd; }

    public BigDecimal getInputCostUsd() { return inputCostUsd; }
    public void setInputCostUsd(BigDecimal inputCostUsd) { this.inputCostUsd = inputCostUsd; }

    public BigDecimal getOutputCostUsd() { return outputCostUsd; }
    public void setOutputCostUsd(BigDecimal outputCostUsd) { this.outputCostUsd = outputCostUsd; }

    public long getReservedCredits() { return reservedCredits; }
    public void setReservedCredits(long reservedCredits) { this.reservedCredits = reservedCredits; }

    public long getReservedPeriodCredits() { return reservedPeriodCredits; }
    public void setReservedPeriodCredits(long reservedPeriodCredits) { this.reservedPeriodCredits = reservedPeriodCredits; }

    public long getReservedBalanceCredits() { return reservedBalanceCredits; }
    public void setReservedBalanceCredits(long reservedBalanceCredits) { this.reservedBalanceCredits = reservedBalanceCredits; }

    public long getSettledCredits() { return settledCredits; }
    public void setSettledCredits(long settledCredits) { this.settledCredits = settledCredits; }

    public long getRefundedCredits() { return refundedCredits; }
    public void setRefundedCredits(long refundedCredits) { this.refundedCredits = refundedCredits; }

    public String getSettlementStatus() { return settlementStatus; }
    public void setSettlementStatus(String settlementStatus) { this.settlementStatus = settlementStatus; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
