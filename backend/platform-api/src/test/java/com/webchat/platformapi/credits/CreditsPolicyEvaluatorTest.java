package com.webchat.platformapi.credits;

import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import com.webchat.platformapi.ai.model.AiModelMetadataService;
import com.webchat.platformapi.auth.role.RolePolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditsPolicyEvaluatorTest {

    private CreditsSystemConfig creditsSystemConfig;
    private CreditsAccountService creditsAccountService;
    private AiModelMetadataService metadataService;
    private RolePolicyService rolePolicyService;
    private CreditsPolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        creditsSystemConfig = mock(CreditsSystemConfig.class);
        creditsAccountService = mock(CreditsAccountService.class);
        metadataService = mock(AiModelMetadataService.class);
        rolePolicyService = mock(RolePolicyService.class);
        evaluator = new CreditsPolicyEvaluator(
                creditsSystemConfig,
                creditsAccountService,
                metadataService,
                rolePolicyService
        );
    }

    @Test
    void evaluateDeniesWhenNegativeDurableBalanceConsumesPeriodicCredits() {
        UUID userId = UUID.randomUUID();
        AiModelMetadataEntity meta = new AiModelMetadataEntity();
        meta.setBillingEnabled(true);

        CreditAccountEntity account = new CreditAccountEntity();
        account.setUserId(userId);
        account.setCreditBalance(-25L);
        account.setPeriodCredits(20L);
        account.setCreditUsed(0L);

        when(creditsSystemConfig.isCreditsSystemEnabled()).thenReturn(true);
        when(creditsSystemConfig.isFreeModeEnabled()).thenReturn(false);
        when(rolePolicyService.resolveAllowedModels(userId, "user")).thenReturn(Set.of("model-a"));
        when(metadataService.findByModelIds(Set.of("model-a"))).thenReturn(Map.of("model-a", meta));
        when(creditsAccountService.getOrCreateUserAccount(userId, "user")).thenReturn(account);

        CreditsPolicyEvaluator.PolicyDecision decision = evaluator.evaluate(userId, null, "user", "model-a");

        assertFalse(decision.allowed());
        assertEquals("credits_insufficient", decision.denialReason());
    }

    @Test
    void evaluateFreeModeBypassesRoleWhitelistAndCreditsAccountChecks() {
        UUID userId = UUID.randomUUID();
        AiModelMetadataEntity meta = new AiModelMetadataEntity();
        meta.setBillingEnabled(true);

        when(creditsSystemConfig.isCreditsSystemEnabled()).thenReturn(true);
        when(creditsSystemConfig.isFreeModeEnabled()).thenReturn(true);
        when(metadataService.findByModelIds(Set.of("model-b"))).thenReturn(Map.of("model-b", meta));

        CreditsPolicyEvaluator.PolicyDecision decision = evaluator.evaluate(userId, null, "guest", "model-b");

        assertTrue(decision.allowed());
        assertFalse(decision.creditsRequired());
        assertEquals(meta, decision.modelMeta());
        assertNull(decision.account());
        verify(rolePolicyService, never()).resolveAllowedModels(userId, "guest");
        verify(creditsAccountService, never()).getOrCreateUserAccount(userId, "guest");
    }
}
