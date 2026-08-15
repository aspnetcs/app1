package com.webchat.platformapi.credits;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CreditsAccountService {

    private final CreditAccountRepository accountRepository;
    private final CreditsRoleTemplateService templateService;

    public CreditsAccountService(CreditAccountRepository accountRepository,
                                 CreditsRoleTemplateService templateService) {
        this.accountRepository = accountRepository;
        this.templateService = templateService;
    }

    @Nullable
    @Transactional
    public CreditAccountEntity getOrCreateUserAccount(@Nullable UUID userId, @Nullable String role) {
        if (userId == null) {
            return null;
        }
        CreditAccountEntity existing = accountRepository.findByUserIdForUpdate(userId).orElse(null);
        if (existing != null) {
            return syncAccount(existing, role, false);
        }
        CreditAccountEntity created = new CreditAccountEntity();
        created.setUserId(userId);
        return createOrReloadUserAccount(userId, role, created);
    }

    @Nullable
    @Transactional
    public CreditAccountEntity getOrCreateGuestAccount(@Nullable String guestId, @Nullable String role) {
        if (guestId == null || guestId.isBlank()) {
            return null;
        }
        String normalizedGuestId = guestId.trim();
        CreditAccountEntity existing = accountRepository.findByGuestIdForUpdate(normalizedGuestId).orElse(null);
        if (existing != null) {
            return syncAccount(existing, role, false);
        }
        CreditAccountEntity created = new CreditAccountEntity();
        created.setGuestId(normalizedGuestId);
        return createOrReloadGuestAccount(normalizedGuestId, role, created);
    }

    @Transactional
    public CreditAccountEntity syncAccount(CreditAccountEntity account, @Nullable String role, boolean forceResetPeriod) {
        CreditAccountEntity workingAccount = lockExistingAccount(account);
        CreditsRoleTemplate template = templateService.resolveTemplate(role);
        Instant now = Instant.now();
        String actualRole = templateService.normalizeRequestedRole(role);
        String normalizedPeriodType = template.periodType();
        boolean isNewAccount = workingAccount.getId() == null;
        boolean roleChanged = !actualRole.equals(templateService.normalizeRequestedRole(workingAccount.getRoleSnapshot()));
        boolean periodCreditsChanged = workingAccount.getPeriodCredits() != template.periodCredits();
        boolean periodTypeChanged = !normalizedPeriodType.equals(templateService.normalizePeriodType(workingAccount.getPeriodType()));
        boolean needsReset = forceResetPeriod || periodTypeChanged || workingAccount.getPeriodStartAt() == null || isPeriodExpired(workingAccount, now);

        if (!isNewAccount && !roleChanged && !periodCreditsChanged && !periodTypeChanged && !needsReset) {
            return workingAccount;
        }

        workingAccount.setRoleSnapshot(actualRole);
        workingAccount.setPeriodCredits(template.periodCredits());
        workingAccount.setPeriodType(normalizedPeriodType);
        if (needsReset) {
            resetPeriodWindow(workingAccount, normalizedPeriodType, now);
        }
        workingAccount.setUpdatedAt(now);
        return accountRepository.save(workingAccount);
    }

    private CreditAccountEntity createOrReloadUserAccount(UUID userId, @Nullable String role, CreditAccountEntity created) {
        try {
            return syncAccount(created, role, false);
        } catch (DataIntegrityViolationException e) {
            CreditAccountEntity existing = accountRepository.findByUserIdForUpdate(userId).orElseThrow(() -> e);
            return syncAccount(existing, role, false);
        }
    }

    private CreditAccountEntity createOrReloadGuestAccount(String guestId, @Nullable String role, CreditAccountEntity created) {
        try {
            return syncAccount(created, role, false);
        } catch (DataIntegrityViolationException e) {
            CreditAccountEntity existing = accountRepository.findByGuestIdForUpdate(guestId).orElseThrow(() -> e);
            return syncAccount(existing, role, false);
        }
    }

    private CreditAccountEntity lockExistingAccount(CreditAccountEntity account) {
        if (account.getId() == null) {
            return account;
        }
        return accountRepository.findByIdForUpdate(account.getId()).orElse(account);
    }

    private boolean isPeriodExpired(CreditAccountEntity account, Instant now) {
        Instant periodEndAt = account.getPeriodEndAt();
        if (periodEndAt == null) {
            return false;
        }
        return !now.isBefore(periodEndAt);
    }

    private void resetPeriodWindow(CreditAccountEntity account, String periodType, Instant now) {
        account.setCreditUsed(0);
        account.setLastResetAt(now);
        account.setPeriodStartAt(now);
        account.setPeriodEndAt(resolvePeriodEnd(now, periodType));
    }

    @Nullable
    private Instant resolvePeriodEnd(Instant start, String periodType) {
        return switch (templateService.normalizePeriodType(periodType)) {
            case "daily" -> start.plus(1, ChronoUnit.DAYS);
            case "weekly" -> start.plus(7, ChronoUnit.DAYS);
            case "monthly" -> start.plus(30, ChronoUnit.DAYS);
            case "none" -> null;
            default -> start.plus(30, ChronoUnit.DAYS);
        };
    }
}
