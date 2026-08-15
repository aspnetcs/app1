package com.webchat.platformapi.credits;

import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime service for Credits reserve / settle / refund operations.
 * All balance mutations happen inside transactions.
 */
@Service
public class CreditsRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(CreditsRuntimeService.class);
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private record CreditAllocation(long periodCredits, long balanceCredits) {
        long totalCredits() {
            return Math.max(0L, periodCredits) + Math.max(0L, balanceCredits);
        }
    }

    private final CreditAccountRepository accountRepo;
    private final CreditSettlementSnapshotRepository snapshotRepo;
    private final CreditsSystemConfig creditsConfig;
    private final CreditsAccountService accountService;

    public CreditsRuntimeService(CreditAccountRepository accountRepo,
                                 CreditSettlementSnapshotRepository snapshotRepo,
                                 CreditsSystemConfig creditsConfig,
                                 CreditsAccountService accountService) {
        this.accountRepo = accountRepo;
        this.snapshotRepo = snapshotRepo;
        this.creditsConfig = creditsConfig;
        this.accountService = accountService;
    }

    // ---- Reserve ----

    /**
     * Reserve credits before streaming starts.
     * Creates a snapshot with status "reserved" and debits estimated credits from the account.
     *
     * @return the snapshot ID, or null if reservation is not needed (free mode / billing disabled)
     */
    @Transactional
    public Long reserve(@Nullable UUID userId, @Nullable String guestId, String role,
                        String modelId, AiModelMetadataEntity modelMeta,
                        CreditAccountEntity account, String requestId) {
        if (!creditsConfig.isCreditsSystemEnabled() || creditsConfig.isFreeModeEnabled()) {
            return null;
        }
        if (modelMeta == null || !modelMeta.isBillingEnabled()) {
            return null;
        }

        int creditsPerUsd = creditsConfig.getCreditsPerUsd();

        // Estimate a modest reservation based on request price only (token costs settle later)
        long estimatedCredits = estimateReservation(modelMeta, creditsPerUsd);
        if (estimatedCredits <= 0) {
            estimatedCredits = 1; // minimum 1 credit reservation
        }

        CreditAccountEntity writableAccount = loadWritableAccount(userId, guestId, role, account);
        if (writableAccount == null) {
            return null;
        }

        long effectiveBalance = writableAccount.getEffectiveBalance();
        long actualReserve = Math.min(estimatedCredits, effectiveBalance);
        if (actualReserve <= 0) {
            log.debug("[credits] reserve skipped due to insufficient locked balance: userId={}, guestId={}, model={}, effectiveBalance={}",
                    userId, guestId, modelId, effectiveBalance);
            return null;
        }

        CreditAllocation reservedAllocation = allocateDebit(writableAccount, actualReserve);
        writableAccount.setUpdatedAt(Instant.now());
        accountRepo.save(writableAccount);

        // Create snapshot
        CreditSettlementSnapshotEntity snapshot = new CreditSettlementSnapshotEntity();
        snapshot.setRequestId(requestId);
        snapshot.setUserId(userId);
        snapshot.setGuestId(guestId);
        snapshot.setRoleSnapshot(role);
        snapshot.setModelId(modelId);
        snapshot.setModelNameSnapshot(modelMeta.getName());
        snapshot.setFreeModeSnapshot(false);
        snapshot.setBillingEnabledSnapshot(true);
        snapshot.setCreditsPerUsdSnapshot(creditsPerUsd);
        snapshot.setRequestPriceUsdSnapshot(modelMeta.getRequestPriceUsd());
        snapshot.setPromptPriceUsdSnapshot(modelMeta.getPromptPriceUsd());
        snapshot.setInputPriceUsdPer1MSnapshot(modelMeta.getInputPriceUsdPer1M());
        snapshot.setOutputPriceUsdPer1MSnapshot(modelMeta.getOutputPriceUsdPer1M());
        snapshot.setReservedCredits(reservedAllocation.totalCredits());
        snapshot.setReservedPeriodCredits(reservedAllocation.periodCredits());
        snapshot.setReservedBalanceCredits(reservedAllocation.balanceCredits());
        snapshot.setSettlementStatus("reserved");
        snapshot.setStartedAt(Instant.now());
        snapshot.setCreatedAt(Instant.now());
        snapshotRepo.save(snapshot);

        log.debug("[credits] reserved: userId={}, guestId={}, model={}, reserved={}, snapshotId={}",
                userId, guestId, modelId, actualReserve, snapshot.getId());

        return snapshot.getId();
    }

    // ---- Settle ----

    /**
     * Settle credits after streaming completes successfully.
     * Calculates actual cost based on real usage and adjusts the balance (refund excess or charge deficit).
     */
    @Transactional
    public void settle(Long snapshotId, int requestCount, int promptCount,
                       int inputTokens, int outputTokens) {
        if (snapshotId == null) {
            return;
        }

        CreditSettlementSnapshotEntity snapshot = snapshotRepo.findById(snapshotId).orElse(null);
        if (snapshot == null) {
            log.warn("[credits] settle: snapshot not found: id={}", snapshotId);
            return;
        }
        if (!"reserved".equals(snapshot.getSettlementStatus())) {
            log.warn("[credits] settle: unexpected status: id={}, status={}", snapshotId, snapshot.getSettlementStatus());
            return;
        }

        // Calculate actual costs
        BigDecimal requestCostUsd = calculateRequestCost(requestCount, snapshot.getRequestPriceUsdSnapshot());
        BigDecimal promptCostUsd = calculatePromptCost(promptCount, snapshot.getPromptPriceUsdSnapshot());
        BigDecimal inputCostUsd = calculateTokenCost(inputTokens, snapshot.getInputPriceUsdPer1MSnapshot());
        BigDecimal outputCostUsd = calculateTokenCost(outputTokens, snapshot.getOutputPriceUsdPer1MSnapshot());

        BigDecimal totalUsdCost = requestCostUsd.add(promptCostUsd).add(inputCostUsd).add(outputCostUsd);
        long settledCredits = totalUsdCost.multiply(BigDecimal.valueOf(snapshot.getCreditsPerUsdSnapshot()))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();

        long reserved = snapshot.getReservedCredits();
        long refundAmount = reserved - settledCredits;

        // Update snapshot
        snapshot.setRequestCount(requestCount);
        snapshot.setPromptCount(promptCount);
        snapshot.setInputTokens(inputTokens);
        snapshot.setOutputTokens(outputTokens);
        snapshot.setRequestCostUsd(requestCostUsd);
        snapshot.setPromptCostUsd(promptCostUsd);
        snapshot.setInputCostUsd(inputCostUsd);
        snapshot.setOutputCostUsd(outputCostUsd);
        snapshot.setSettledCredits(settledCredits);
        snapshot.setRefundedCredits(Math.max(0, refundAmount));
        snapshot.setSettlementStatus("settled");
        snapshot.setSettledAt(Instant.now());
        snapshotRepo.save(snapshot);

        if (refundAmount != 0) {
            CreditAccountEntity account = findAccountForUpdate(snapshot.getUserId(), snapshot.getGuestId());
            if (account != null) {
                if (refundAmount > 0) {
                    refundReservedCredits(account, snapshot, settledCredits);
                } else {
                    allocateDebit(account, Math.abs(refundAmount));
                }
                account.setUpdatedAt(Instant.now());
                accountRepo.save(account);
            }
        }

        log.debug("[credits] settled: snapshotId={}, reserved={}, settled={}, refund={}",
                snapshotId, reserved, settledCredits, Math.max(0, refundAmount));
    }

    // ---- Refund ----

    /**
     * Full refund when streaming fails before any content is sent.
     */
    @Transactional
    public void refund(Long snapshotId) {
        if (snapshotId == null) {
            return;
        }

        CreditSettlementSnapshotEntity snapshot = snapshotRepo.findById(snapshotId).orElse(null);
        if (snapshot == null) {
            log.warn("[credits] refund: snapshot not found: id={}", snapshotId);
            return;
        }
        if (!"reserved".equals(snapshot.getSettlementStatus())) {
            log.warn("[credits] refund: unexpected status: id={}, status={}", snapshotId, snapshot.getSettlementStatus());
            return;
        }

        long reserved = snapshot.getReservedCredits();

        // Update snapshot
        snapshot.setSettledCredits(0);
        snapshot.setRefundedCredits(reserved);
        snapshot.setSettlementStatus("refunded");
        snapshot.setSettledAt(Instant.now());
        snapshotRepo.save(snapshot);

        // Give back credits
        CreditAccountEntity account = findAccountForUpdate(snapshot.getUserId(), snapshot.getGuestId());
        if (account != null && reserved > 0) {
            refundAllocation(account, snapshot.getReservedPeriodCredits(), snapshot.getReservedBalanceCredits());
            account.setUpdatedAt(Instant.now());
            accountRepo.save(account);
        }

        log.debug("[credits] refunded: snapshotId={}, refunded={}", snapshotId, reserved);
    }

    /**
     * Partial refund when streaming was interrupted after some content was sent.
     */
    @Transactional
    public void partialSettle(Long snapshotId, int inputTokens, int outputTokens) {
        if (snapshotId == null) {
            return;
        }

        CreditSettlementSnapshotEntity snapshot = snapshotRepo.findById(snapshotId).orElse(null);
        if (snapshot == null) {
            return;
        }
        if (!"reserved".equals(snapshot.getSettlementStatus())) {
            return;
        }

        // Settle with whatever tokens were consumed
        BigDecimal inputCostUsd = calculateTokenCost(inputTokens, snapshot.getInputPriceUsdPer1MSnapshot());
        BigDecimal outputCostUsd = calculateTokenCost(outputTokens, snapshot.getOutputPriceUsdPer1MSnapshot());
        // Count 1 request + 1 prompt for partial
        BigDecimal requestCostUsd = calculateRequestCost(1, snapshot.getRequestPriceUsdSnapshot());
        BigDecimal promptCostUsd = calculatePromptCost(1, snapshot.getPromptPriceUsdSnapshot());

        BigDecimal totalUsdCost = requestCostUsd.add(promptCostUsd).add(inputCostUsd).add(outputCostUsd);
        long settledCredits = totalUsdCost.multiply(BigDecimal.valueOf(snapshot.getCreditsPerUsdSnapshot()))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();

        long reserved = snapshot.getReservedCredits();
        long refundAmount = Math.max(0, reserved - settledCredits);

        snapshot.setRequestCount(1);
        snapshot.setPromptCount(1);
        snapshot.setInputTokens(inputTokens);
        snapshot.setOutputTokens(outputTokens);
        snapshot.setRequestCostUsd(requestCostUsd);
        snapshot.setPromptCostUsd(promptCostUsd);
        snapshot.setInputCostUsd(inputCostUsd);
        snapshot.setOutputCostUsd(outputCostUsd);
        snapshot.setSettledCredits(settledCredits);
        snapshot.setRefundedCredits(refundAmount);
        snapshot.setSettlementStatus("partial_refund");
        snapshot.setSettledAt(Instant.now());
        snapshotRepo.save(snapshot);

        CreditAccountEntity account = findAccountForUpdate(snapshot.getUserId(), snapshot.getGuestId());
        if (account != null) {
            if (settledCredits <= reserved) {
                refundReservedCredits(account, snapshot, settledCredits);
            } else {
                allocateDebit(account, settledCredits - reserved);
            }
            account.setUpdatedAt(Instant.now());
            accountRepo.save(account);
        }

        log.debug("[credits] partial settle: snapshotId={}, reserved={}, settled={}, refund={}",
                snapshotId, reserved, settledCredits, refundAmount);
    }

    // ---- Settlement history ----

    public List<CreditSettlementSnapshotEntity> getHistory(@Nullable UUID userId, @Nullable String guestId) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 100);
        return getHistoryPage(userId, guestId, pageable).getContent();
    }

    public org.springframework.data.domain.Page<CreditSettlementSnapshotEntity> getHistoryPage(
            @Nullable UUID userId,
            @Nullable String guestId,
            org.springframework.data.domain.Pageable pageable
    ) {
        if (userId != null) {
            return snapshotRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        if (guestId != null) {
            return snapshotRepo.findByGuestIdOrderByCreatedAtDesc(guestId, pageable);
        }
        return org.springframework.data.domain.Page.empty(pageable);
    }

    public Map<String, Object> getAccountSummary(@Nullable UUID userId, @Nullable String guestId) {
        return getAccountSummary(userId, guestId, null);
    }

    public Map<String, Object> getAccountSummary(@Nullable UUID userId, @Nullable String guestId, @Nullable String role) {
        if (creditsConfig.isCreditsSystemEnabled()) {
            if (userId != null) {
                CreditAccountEntity account = accountService.getOrCreateUserAccount(userId, role);
                return buildAccountSummary(account);
            } else if (guestId != null && !guestId.isBlank()) {
                CreditAccountEntity account = accountService.getOrCreateGuestAccount(guestId, role);
                return buildAccountSummary(account);
            }
        }
        CreditAccountEntity account = findAccount(userId, guestId);
        return buildAccountSummary(account);
    }

    private Map<String, Object> buildAccountSummary(@Nullable CreditAccountEntity account) {
        if (account == null) {
            return Map.of(
                    "hasAccount", false,
                    "creditsSystemEnabled", creditsConfig.isCreditsSystemEnabled(),
                    "freeModeEnabled", creditsConfig.isFreeModeEnabled()
            );
        }
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("hasAccount", true);
        summary.put("creditsSystemEnabled", creditsConfig.isCreditsSystemEnabled());
        summary.put("freeModeEnabled", creditsConfig.isFreeModeEnabled());
        summary.put("creditBalance", account.getCreditBalance());
        summary.put("creditUsed", account.getCreditUsed());
        summary.put("manualCreditAdjustment", account.getManualCreditAdjustment());
        summary.put("manualAdjustment", account.getManualCreditAdjustment());
        summary.put("effectiveBalance", account.getEffectiveBalance());
        summary.put("periodCredits", account.getPeriodCredits());
        summary.put("periodType", account.getPeriodType());
        summary.put("periodStartAt", account.getPeriodStartAt());
        summary.put("periodEndAt", account.getPeriodEndAt());
        summary.put("role", account.getRoleSnapshot());
        summary.put("unlimited", account.isUnlimitedPeriodCredits());
        return summary;
    }

    // ---- Calculation helpers ----

    private long estimateReservation(AiModelMetadataEntity meta, int creditsPerUsd) {
        // Estimate based on request price + a small token buffer
        BigDecimal requestPrice = meta.getRequestPriceUsd() != null ? meta.getRequestPriceUsd() : BigDecimal.ZERO;
        BigDecimal promptPrice = meta.getPromptPriceUsd() != null ? meta.getPromptPriceUsd() : BigDecimal.ZERO;
        BigDecimal estimate = requestPrice.add(promptPrice).multiply(BigDecimal.valueOf(creditsPerUsd));
        long estimated = estimate.setScale(0, RoundingMode.CEILING).longValueExact();
        return Math.max(estimated, 1);
    }

    private static BigDecimal calculateRequestCost(int requestCount, BigDecimal pricePerRequest) {
        if (pricePerRequest == null || requestCount <= 0) {
            return BigDecimal.ZERO;
        }
        return pricePerRequest.multiply(BigDecimal.valueOf(requestCount));
    }

    private static BigDecimal calculatePromptCost(int promptCount, BigDecimal pricePerPrompt) {
        if (pricePerPrompt == null || promptCount <= 0) {
            return BigDecimal.ZERO;
        }
        return pricePerPrompt.multiply(BigDecimal.valueOf(promptCount));
    }

    private static BigDecimal calculateTokenCost(int tokens, BigDecimal pricePer1M) {
        if (pricePer1M == null || tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens).divide(ONE_MILLION, 12, RoundingMode.HALF_UP).multiply(pricePer1M);
    }

    @Nullable
    private CreditAccountEntity loadWritableAccount(@Nullable UUID userId,
                                                    @Nullable String guestId,
                                                    @Nullable String role,
                                                    @Nullable CreditAccountEntity fallbackAccount) {
        if (userId != null) {
            return accountService.getOrCreateUserAccount(userId, role);
        }
        if (guestId != null && !guestId.isBlank()) {
            return accountService.getOrCreateGuestAccount(guestId, role);
        }
        return fallbackAccount;
    }

    private CreditAllocation allocateDebit(CreditAccountEntity account, long requestedCredits) {
        long safeRequestedCredits = Math.max(0L, requestedCredits);
        if (safeRequestedCredits == 0L) {
            return new CreditAllocation(0L, 0L);
        }
        if (account.isUnlimitedPeriodCredits()) {
            account.setCreditUsed(Math.max(0L, account.getCreditUsed()) + safeRequestedCredits);
            return new CreditAllocation(safeRequestedCredits, 0L);
        }
        long periodCredits = Math.min(account.getRemainingPeriodicCredits(), safeRequestedCredits);
        long balanceCredits = safeRequestedCredits - periodCredits;
        if (periodCredits > 0) {
            account.setCreditUsed(Math.max(0L, account.getCreditUsed()) + periodCredits);
        }
        if (balanceCredits > 0) {
            account.setCreditBalance(account.getCreditBalance() - balanceCredits);
        }
        return new CreditAllocation(periodCredits, balanceCredits);
    }

    private void refundReservedCredits(CreditAccountEntity account,
                                       CreditSettlementSnapshotEntity snapshot,
                                       long settledCredits) {
        long safeSettledCredits = Math.max(0L, settledCredits);
        long settledPeriodCredits = Math.min(safeSettledCredits, Math.max(0L, snapshot.getReservedPeriodCredits()));
        long settledBalanceCredits = Math.max(0L, safeSettledCredits - settledPeriodCredits);
        long refundPeriodCredits = Math.max(0L, snapshot.getReservedPeriodCredits() - settledPeriodCredits);
        long refundBalanceCredits = Math.max(0L, snapshot.getReservedBalanceCredits() - settledBalanceCredits);
        refundAllocation(account, refundPeriodCredits, refundBalanceCredits);
    }

    private void refundAllocation(CreditAccountEntity account, long periodCredits, long balanceCredits) {
        long safePeriodCredits = Math.max(0L, periodCredits);
        long safeBalanceCredits = Math.max(0L, balanceCredits);
        if (safePeriodCredits > 0) {
            account.setCreditUsed(Math.max(0L, account.getCreditUsed()) - safePeriodCredits);
        }
        if (safeBalanceCredits > 0) {
            account.setCreditBalance(account.getCreditBalance() + safeBalanceCredits);
        }
    }

    @Nullable
    private CreditAccountEntity findAccount(@Nullable UUID userId, @Nullable String guestId) {
        if (userId != null) {
            return accountRepo.findByUserId(userId).orElse(null);
        }
        if (guestId != null && !guestId.isBlank()) {
            return accountRepo.findByGuestId(guestId).orElse(null);
        }
        return null;
    }

    @Nullable
    private CreditAccountEntity findAccountForUpdate(@Nullable UUID userId, @Nullable String guestId) {
        if (userId != null) {
            return accountRepo.findByUserIdForUpdate(userId).orElse(null);
        }
        if (guestId != null && !guestId.isBlank()) {
            return accountRepo.findByGuestIdForUpdate(guestId).orElse(null);
        }
        return null;
    }
}
