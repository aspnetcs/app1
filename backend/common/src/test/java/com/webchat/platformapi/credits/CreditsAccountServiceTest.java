package com.webchat.platformapi.credits;

import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.config.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditsAccountServiceTest {

    private CreditAccountRepository accountRepository;
    private RolePolicyProperties rolePolicyProperties;
    private SysConfigService sysConfigService;
    private CreditsAccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(CreditAccountRepository.class);
        rolePolicyProperties = mock(RolePolicyProperties.class);
        sysConfigService = mock(SysConfigService.class);
        CreditsRoleTemplateService templateService = new CreditsRoleTemplateService(rolePolicyProperties, sysConfigService);
        accountService = new CreditsAccountService(accountRepository, templateService);

        when(accountRepository.save(any(CreditAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, CreditAccountEntity.class));
    }

    @Test
    void createsPendingAccountUsingUserTemplateDefaults() {
        UUID userId = UUID.randomUUID();
        RolePolicyProperties.RoleConfig userPolicy = new RolePolicyProperties.RoleConfig();
        userPolicy.setPeriodCredits(240);
        userPolicy.setPeriodType("weekly");

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(rolePolicyProperties.getPolicy("user")).thenReturn(userPolicy);
        when(sysConfigService.getLong("credits.role.user.periodCredits", 240L)).thenReturn(240L);
        when(sysConfigService.getOrDefault("credits.role.user.periodType", "weekly")).thenReturn("weekly");

        CreditAccountEntity account = accountService.getOrCreateUserAccount(userId, "pending");

        assertNotNull(account);
        assertEquals("pending", account.getRoleSnapshot());
        assertEquals(240L, account.getPeriodCredits());
        assertEquals("weekly", account.getPeriodType());
        assertEquals(0L, account.getCreditUsed());
        assertNotNull(account.getPeriodStartAt());
        assertNotNull(account.getPeriodEndAt());
        assertTrue(account.getPeriodEndAt().isAfter(account.getPeriodStartAt()));
    }

    @Test
    void resetsExpiredPeriodWhenSyncingExistingAccount() {
        UUID userId = UUID.randomUUID();
        RolePolicyProperties.RoleConfig userPolicy = new RolePolicyProperties.RoleConfig();
        userPolicy.setPeriodCredits(80);
        userPolicy.setPeriodType("daily");

        CreditAccountEntity existing = new CreditAccountEntity();
        existing.setUserId(userId);
        existing.setRoleSnapshot("user");
        existing.setPeriodCredits(80);
        existing.setPeriodType("daily");
        existing.setCreditUsed(55);
        existing.setPeriodStartAt(Instant.now().minusSeconds(172800));
        existing.setPeriodEndAt(Instant.now().minusSeconds(3600));

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(rolePolicyProperties.getPolicy("user")).thenReturn(userPolicy);
        when(sysConfigService.getLong("credits.role.user.periodCredits", 80L)).thenReturn(80L);
        when(sysConfigService.getOrDefault("credits.role.user.periodType", "daily")).thenReturn("daily");

        CreditAccountEntity synced = accountService.getOrCreateUserAccount(userId, "user");

        assertEquals(0L, synced.getCreditUsed());
        assertNotNull(synced.getLastResetAt());
        assertNotNull(synced.getPeriodStartAt());
        assertNotNull(synced.getPeriodEndAt());
        assertTrue(synced.getPeriodEndAt().isAfter(synced.getPeriodStartAt()));
    }

    @Test
    void doesNotPersistWhenExistingAccountAlreadyMatchesCurrentTemplate() {
        UUID userId = UUID.randomUUID();
        RolePolicyProperties.RoleConfig userPolicy = new RolePolicyProperties.RoleConfig();
        userPolicy.setPeriodCredits(80);
        userPolicy.setPeriodType("daily");

        CreditAccountEntity existing = new CreditAccountEntity();
        existing.setId(11L);
        existing.setUserId(userId);
        existing.setRoleSnapshot("user");
        existing.setPeriodCredits(80);
        existing.setPeriodType("daily");
        existing.setCreditUsed(20);
        existing.setPeriodStartAt(Instant.now().minusSeconds(600));
        existing.setPeriodEndAt(Instant.now().plusSeconds(3600));

        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(existing));
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(existing));
        when(rolePolicyProperties.getPolicy("user")).thenReturn(userPolicy);
        when(sysConfigService.getLong("credits.role.user.periodCredits", 80L)).thenReturn(80L);
        when(sysConfigService.getOrDefault("credits.role.user.periodType", "daily")).thenReturn("daily");

        CreditAccountEntity synced = accountService.getOrCreateUserAccount(userId, "user");

        assertEquals(existing, synced);
        verify(accountRepository, never()).save(any(CreditAccountEntity.class));
    }
}
