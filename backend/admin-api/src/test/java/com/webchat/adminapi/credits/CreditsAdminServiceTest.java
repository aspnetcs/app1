package com.webchat.adminapi.credits;

import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.credits.CreditAccountEntity;
import com.webchat.platformapi.credits.CreditAccountRepository;
import com.webchat.platformapi.credits.CreditsAccountService;
import com.webchat.platformapi.credits.CreditsRoleTemplate;
import com.webchat.platformapi.credits.CreditsRoleTemplateService;
import com.webchat.platformapi.credits.CreditsSystemConfig;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditsAdminServiceTest {

    @Mock
    private CreditsSystemConfig creditsSystemConfig;
    @Mock
    private CreditsRoleTemplateService creditsRoleTemplateService;
    @Mock
    private CreditsAccountService creditsAccountService;
    @Mock
    private CreditAccountRepository creditAccountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private CreditsAdminService service;

    @BeforeEach
    void setUp() {
        service = new CreditsAdminService(
                creditsSystemConfig,
                creditsRoleTemplateService,
                creditsAccountService,
                creditAccountRepository,
                userRepository,
                jdbcTemplate
        );
    }

    @Test
    void getSystemConfigReturnsTypedPayload() {
        when(creditsSystemConfig.isCreditsSystemEnabled()).thenReturn(true);
        when(creditsSystemConfig.getCreditsPerUsd()).thenReturn(2500);
        when(creditsSystemConfig.isFreeModeEnabled()).thenReturn(false);

        var response = service.getSystemConfig();

        assertEquals(0, response.code());
        assertEquals(true, response.data().get("creditsSystemEnabled"));
        assertEquals(2500, response.data().get("creditsPerUsd"));
        assertEquals(false, response.data().get("freeModeEnabled"));
    }

    @Test
    void updateSystemConfigRejectsNonPositiveCreditsPerUsd() {
        var response = service.updateSystemConfig(true, 0, false);

        assertEquals(ErrorCodes.PARAM_INVALID, response.code());
    }

    @Test
    void listRoleTemplatesAddsPendingCompatibilityRow() {
        when(creditsRoleTemplateService.listEditableTemplates()).thenReturn(List.of(
                new CreditsRoleTemplate("user", "user", true, 1000L, "monthly", false)
        ));
        when(creditsRoleTemplateService.resolveTemplate("pending"))
                .thenReturn(new CreditsRoleTemplate("pending", "user", false, 1000L, "monthly", false));

        var response = service.listRoleTemplates();

        assertEquals(0, response.code());
        List<?> items = (List<?>) response.data().get("items");
        assertEquals(2, items.size());
        assertTrue(items.stream().map(Map.class::cast).anyMatch(item -> "pending".equals(item.get("role"))));
    }

    @Test
    void getUserAccountDetailUsesSyncedAccountWhenCreditsSystemIsEnabled() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole("premium");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        CreditAccountEntity syncedAccount = new CreditAccountEntity();
        syncedAccount.setUserId(userId);
        syncedAccount.setRoleSnapshot("premium");
        syncedAccount.setPeriodCredits(3000L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(creditsSystemConfig.isCreditsSystemEnabled()).thenReturn(true);
        when(creditsAccountService.getOrCreateUserAccount(userId, "premium")).thenReturn(syncedAccount);
        when(creditsRoleTemplateService.resolveTemplate("premium"))
                .thenReturn(new CreditsRoleTemplate("premium", "premium", true, 3000L, "monthly", false));

        var response = service.getUserAccountDetail(userId);

        assertEquals(0, response.code());
        Map<?, ?> accountPayload = (Map<?, ?>) response.data().get("account");
        assertTrue((Boolean) accountPayload.get("exists"));
        Map<?, ?> template = (Map<?, ?>) response.data().get("template");
        assertEquals(3000L, template.get("periodCredits"));
    }

    @Test
    void adjustUserAccountPersistsManualCreditDelta() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole("user");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        CreditAccountEntity account = new CreditAccountEntity();
        account.setUserId(userId);
        account.setManualCreditAdjustment(200L);
        account.setPeriodCredits(1000L);
        account.setCreditUsed(100L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(creditsAccountService.getOrCreateUserAccount(userId, "user")).thenReturn(account);
        when(creditAccountRepository.save(any(CreditAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditsRoleTemplateService.resolveTemplate("user"))
                .thenReturn(new CreditsRoleTemplate("user", "user", true, 1000L, "monthly", false));

        var response = service.adjustUserAccount(userId, 500L, "manual grant");

        assertEquals(0, response.code());
        ArgumentCaptor<CreditAccountEntity> captor = ArgumentCaptor.forClass(CreditAccountEntity.class);
        verify(creditAccountRepository).save(captor.capture());
        assertEquals(700L, captor.getValue().getManualCreditAdjustment());
        assertEquals(500L, captor.getValue().getCreditBalance());
    }

    @Test
    void grantOrSyncUserAccountUsesCurrentRoleByDefault() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole("premium");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        CreditAccountEntity account = new CreditAccountEntity();
        account.setUserId(userId);
        account.setRoleSnapshot("premium");
        account.setPeriodCredits(3000L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(creditsAccountService.getOrCreateUserAccount(userId, "premium")).thenReturn(account);
        when(creditsRoleTemplateService.resolveTemplate("premium"))
                .thenReturn(new CreditsRoleTemplate("premium", "premium", true, 3000L, "monthly", false));

        var response = service.grantOrSyncUserAccount(userId, null, false);

        assertEquals(0, response.code());
        Map<?, ?> action = (Map<?, ?>) response.data().get("action");
        assertEquals("premium", action.get("appliedRole"));
    }

    @Test
    void listSettlementsBuildsFilteredLedgerPayload() {
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Long.class))).thenReturn(1L);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", 11L);
        row.put("request_id", "req-1");
        row.put("user_id", userId);
        row.put("guest_id", "");
        row.put("role_snapshot", "user");
        row.put("model_id", "gpt-4.1");
        row.put("model_name_snapshot", "GPT 4.1");
        row.put("free_mode_snapshot", false);
        row.put("billing_enabled_snapshot", true);
        row.put("credits_per_usd_snapshot", 1000);
        row.put("request_price_usd_snapshot", BigDecimal.ZERO);
        row.put("prompt_price_usd_snapshot", BigDecimal.ZERO);
        row.put("input_price_usd_per_1m_snapshot", BigDecimal.ONE);
        row.put("output_price_usd_per_1m_snapshot", BigDecimal.ONE);
        row.put("request_count", 1);
        row.put("prompt_count", 0);
        row.put("input_tokens", 120);
        row.put("output_tokens", 80);
        row.put("request_cost_usd", BigDecimal.ZERO);
        row.put("prompt_cost_usd", BigDecimal.ZERO);
        row.put("input_cost_usd", BigDecimal.ONE);
        row.put("output_cost_usd", BigDecimal.ONE);
        row.put("reserved_credits", 300L);
        row.put("settled_credits", 280L);
        row.put("refunded_credits", 20L);
        row.put("settlement_status", "settled");
        row.put("failure_reason", "");
        row.put("started_at", Instant.now());
        row.put("settled_at", Instant.now());
        row.put("created_at", Instant.now());
        row.put("user_email", "demo@example.com");
        row.put("user_phone", "13800000000");
        row.put("current_user_role", "user");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        var response = service.listSettlements(0, 20, userId.toString(), null, "req-1", "settled", "gpt-4.1");

        assertEquals(0, response.code());
        assertEquals(1L, response.data().get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) response.data().get("pageSummary");
        assertEquals(300L, summary.get("reservedCredits"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("AND s.user_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("AND s.request_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("AND s.settlement_status = ?"));
        assertTrue(sqlCaptor.getValue().contains("AND s.model_id = ?"));
    }
}
