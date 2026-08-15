package com.webchat.platformapi.credits;

import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditsRuntimeServiceTest {

    @Mock
    private CreditAccountRepository accountRepository;

    @Mock
    private CreditSettlementSnapshotRepository snapshotRepository;

    @Mock
    private CreditsSystemConfig creditsSystemConfig;

    @Mock
    private CreditsAccountService creditsAccountService;

    private CreditsRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new CreditsRuntimeService(accountRepository, snapshotRepository, creditsSystemConfig, creditsAccountService);
    }

    // Only the reserve path needs credits config stubs.
    private void stubCreditsConfig() {
        when(creditsSystemConfig.isCreditsSystemEnabled()).thenReturn(true);
        when(creditsSystemConfig.isFreeModeEnabled()).thenReturn(false);
        when(creditsSystemConfig.getCreditsPerUsd()).thenReturn(100);
    }

    @Test
    void reserveSplitsPeriodicAllowanceBeforeDurableBalance() {
        UUID userId = UUID.randomUUID();
        AiModelMetadataEntity meta = new AiModelMetadataEntity();
        meta.setBillingEnabled(true);
        meta.setRequestPriceUsd(new BigDecimal("1.20"));

        stubCreditsConfig();

        CreditAccountEntity account = new CreditAccountEntity();
        account.setId(1L);
        account.setUserId(userId);
        account.setPeriodCredits(100L);
        account.setCreditUsed(20L);
        account.setCreditBalance(50L);

        when(creditsAccountService.getOrCreateUserAccount(userId, "user")).thenReturn(account);

        service.reserve(userId, null, "user", "model-a", meta, account, "req-1");

        ArgumentCaptor<CreditSettlementSnapshotEntity> snapshotCaptor =
                ArgumentCaptor.forClass(CreditSettlementSnapshotEntity.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        CreditSettlementSnapshotEntity snapshot = snapshotCaptor.getValue();

        assertEquals(100L, account.getCreditUsed());
        assertEquals(10L, account.getCreditBalance());
        assertEquals(120L, snapshot.getReservedCredits());
        assertEquals(80L, snapshot.getReservedPeriodCredits());
        assertEquals(40L, snapshot.getReservedBalanceCredits());
    }

    @Test
    void settleRefundsPeriodicAndDurableBalanceToMatchActualCost() {
        UUID userId = UUID.randomUUID();
        CreditSettlementSnapshotEntity snapshot = new CreditSettlementSnapshotEntity();
        snapshot.setId(7L);
        snapshot.setUserId(userId);
        snapshot.setSettlementStatus("reserved");
        snapshot.setReservedCredits(80L);
        snapshot.setReservedPeriodCredits(60L);
        snapshot.setReservedBalanceCredits(20L);
        snapshot.setCreditsPerUsdSnapshot(100);
        snapshot.setRequestPriceUsdSnapshot(new BigDecimal("0.50"));

        CreditAccountEntity account = new CreditAccountEntity();
        account.setId(3L);
        account.setUserId(userId);
        account.setPeriodCredits(100L);
        account.setCreditUsed(60L);
        account.setCreditBalance(80L);

        when(snapshotRepository.findById(7L)).thenReturn(Optional.of(snapshot));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        service.settle(7L, 1, 0, 0, 0);

        assertEquals(50L, snapshot.getSettledCredits());
        assertEquals(30L, snapshot.getRefundedCredits());
        assertEquals("settled", snapshot.getSettlementStatus());
        assertEquals(50L, account.getCreditUsed());
        assertEquals(100L, account.getCreditBalance());
    }

    @Test
    void settleChargesSingleTokenProportionallyInsteadOfFullMillionBlock() {
        UUID userId = UUID.randomUUID();
        CreditSettlementSnapshotEntity snapshot = new CreditSettlementSnapshotEntity();
        snapshot.setId(8L);
        snapshot.setUserId(userId);
        snapshot.setSettlementStatus("reserved");
        snapshot.setReservedCredits(10L);
        snapshot.setReservedPeriodCredits(10L);
        snapshot.setReservedBalanceCredits(0L);
        snapshot.setCreditsPerUsdSnapshot(1000);
        snapshot.setInputPriceUsdPer1MSnapshot(new BigDecimal("1.00"));
        snapshot.setOutputPriceUsdPer1MSnapshot(BigDecimal.ZERO);
        snapshot.setRequestPriceUsdSnapshot(BigDecimal.ZERO);
        snapshot.setPromptPriceUsdSnapshot(BigDecimal.ZERO);

        CreditAccountEntity account = new CreditAccountEntity();
        account.setId(8L);
        account.setUserId(userId);
        account.setPeriodCredits(100L);
        account.setCreditUsed(10L);
        account.setCreditBalance(30L);

        when(snapshotRepository.findById(8L)).thenReturn(Optional.of(snapshot));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        service.settle(8L, 0, 0, 1, 0);

        assertEquals(1L, snapshot.getSettledCredits());
        assertEquals(9L, snapshot.getRefundedCredits());
        assertEquals(1L, account.getCreditUsed());
        assertEquals(30L, account.getCreditBalance());
    }

    @Test
    void partialSettleChargesAdditionalBalanceWhenActualCostExceedsReservation() {
        UUID userId = UUID.randomUUID();
        CreditSettlementSnapshotEntity snapshot = new CreditSettlementSnapshotEntity();
        snapshot.setId(9L);
        snapshot.setUserId(userId);
        snapshot.setSettlementStatus("reserved");
        snapshot.setReservedCredits(80L);
        snapshot.setReservedPeriodCredits(60L);
        snapshot.setReservedBalanceCredits(20L);
        snapshot.setCreditsPerUsdSnapshot(100);
        snapshot.setRequestPriceUsdSnapshot(new BigDecimal("0.95"));
        snapshot.setPromptPriceUsdSnapshot(BigDecimal.ZERO);

        CreditAccountEntity account = new CreditAccountEntity();
        account.setId(4L);
        account.setUserId(userId);
        account.setPeriodCredits(60L);
        account.setCreditUsed(60L);
        account.setCreditBalance(30L);

        when(snapshotRepository.findById(9L)).thenReturn(Optional.of(snapshot));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        service.partialSettle(9L, 0, 0);

        assertEquals(95L, snapshot.getSettledCredits());
        assertEquals(0L, snapshot.getRefundedCredits());
        assertEquals("partial_refund", snapshot.getSettlementStatus());
        assertEquals(60L, account.getCreditUsed());
        assertEquals(15L, account.getCreditBalance());
    }

    @Test
    void settleUsesSnapshotCreditsPerUsdInsteadOfCurrentConfig() {
        UUID userId = UUID.randomUUID();
        CreditSettlementSnapshotEntity snapshot = new CreditSettlementSnapshotEntity();
        snapshot.setId(10L);
        snapshot.setUserId(userId);
        snapshot.setSettlementStatus("reserved");
        snapshot.setReservedCredits(60L);
        snapshot.setReservedPeriodCredits(60L);
        snapshot.setReservedBalanceCredits(0L);
        snapshot.setCreditsPerUsdSnapshot(100);
        snapshot.setRequestPriceUsdSnapshot(new BigDecimal("0.50"));
        snapshot.setPromptPriceUsdSnapshot(BigDecimal.ZERO);
        snapshot.setInputPriceUsdPer1MSnapshot(BigDecimal.ZERO);
        snapshot.setOutputPriceUsdPer1MSnapshot(BigDecimal.ZERO);

        CreditAccountEntity account = new CreditAccountEntity();
        account.setId(10L);
        account.setUserId(userId);
        account.setPeriodCredits(100L);
        account.setCreditUsed(60L);
        account.setCreditBalance(20L);

        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapshot));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        service.settle(10L, 1, 0, 0, 0);

        assertEquals(50L, snapshot.getSettledCredits());
        assertEquals(10L, snapshot.getRefundedCredits());
        assertEquals(50L, account.getCreditUsed());
        assertEquals(20L, account.getCreditBalance());
        verify(creditsSystemConfig, never()).getCreditsPerUsd();
    }

    @Test
    void effectiveBalanceLetsNegativeDurableBalanceReduceSpendableCredits() {
        CreditAccountEntity account = new CreditAccountEntity();
        account.setPeriodCredits(10L);
        account.setCreditUsed(3L);
        account.setCreditBalance(-5L);

        assertEquals(2L, account.getEffectiveBalance());

        account.setCreditBalance(-9L);
        assertEquals(0L, account.getEffectiveBalance());
    }

    @Test
    void reserveSkipsSnapshotWhenBalanceIsAlreadyDrained() {
        UUID userId = UUID.randomUUID();
        AiModelMetadataEntity meta = new AiModelMetadataEntity();
        meta.setBillingEnabled(true);
        meta.setRequestPriceUsd(BigDecimal.ZERO);
        meta.setPromptPriceUsd(BigDecimal.ZERO);

        stubCreditsConfig();

        CreditAccountEntity account = new CreditAccountEntity();
        account.setId(5L);
        account.setUserId(userId);
        account.setPeriodCredits(0L);
        account.setCreditUsed(0L);
        account.setCreditBalance(0L);

        when(creditsAccountService.getOrCreateUserAccount(userId, "user")).thenReturn(account);

        Long snapshotId = service.reserve(userId, null, "user", "model-a", meta, account, "req-zero");

        assertNull(snapshotId);
        assertEquals(0L, account.getCreditUsed());
        assertEquals(0L, account.getCreditBalance());
        verify(snapshotRepository, never()).save(any(CreditSettlementSnapshotEntity.class));
        verify(accountRepository, never()).save(any(CreditAccountEntity.class));
    }
}
