package com.webchat.platformapi.credits;

import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import com.webchat.platformapi.ai.model.AiModelMetadataService;
import com.webchat.platformapi.auth.role.RolePolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Evaluates whether a user/guest request should proceed under the Credits system.
 * Returns a structured decision with denial reason when applicable.
 */
@Component
public class CreditsPolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CreditsPolicyEvaluator.class);
    private static final String GUEST_ROLE = "guest";

    private final CreditsSystemConfig creditsConfig;
    private final CreditsAccountService accountService;
    private final AiModelMetadataService metadataService;
    private final RolePolicyService rolePolicyService;

    public CreditsPolicyEvaluator(CreditsSystemConfig creditsConfig,
                                  CreditsAccountService accountService,
                                  AiModelMetadataService metadataService,
                                  RolePolicyService rolePolicyService) {
        this.creditsConfig = creditsConfig;
        this.accountService = accountService;
        this.metadataService = metadataService;
        this.rolePolicyService = rolePolicyService;
    }

    public record PolicyDecision(boolean allowed, String denialReason, boolean creditsRequired,
                                 AiModelMetadataEntity modelMeta, CreditAccountEntity account) {
        public static PolicyDecision allow(AiModelMetadataEntity meta, CreditAccountEntity account) {
            return new PolicyDecision(true, null, true, meta, account);
        }

        public static PolicyDecision allowFreeMode(AiModelMetadataEntity meta) {
            return new PolicyDecision(true, null, false, meta, null);
        }

        public static PolicyDecision allowNoBilling(AiModelMetadataEntity meta) {
            return new PolicyDecision(true, null, false, meta, null);
        }

        public static PolicyDecision allowCreditsDisabled() {
            return new PolicyDecision(true, null, false, null, null);
        }

        public static PolicyDecision deny(String reason) {
            return new PolicyDecision(false, reason, false, null, null);
        }
    }

    public PolicyDecision evaluate(@Nullable UUID userId, @Nullable String guestId, String role, String modelId) {
        if (!creditsConfig.isCreditsSystemEnabled()) {
            return PolicyDecision.allowCreditsDisabled();
        }

        if (creditsConfig.isFreeModeEnabled()) {
            AiModelMetadataEntity meta = lookupMeta(modelId);
            return PolicyDecision.allowFreeMode(meta);
        }

        if (isGuestRole(role)) {
            AiModelMetadataEntity meta = lookupMeta(modelId);
            return PolicyDecision.allowNoBilling(meta);
        }

        // Check allowed models (role-based) unless free mode
        if (userId != null && rolePolicyService != null) {
            Set<String> allowed = rolePolicyService.resolveAllowedModels(userId, role);
            if (!allowed.isEmpty() && !allowed.contains(modelId)) {
                return PolicyDecision.deny("model_not_allowed");
            }
        }

        AiModelMetadataEntity meta = lookupMeta(modelId);

        // If model has no billing enabled, allow without credits check
        if (meta == null || !meta.isBillingEnabled()) {
            return PolicyDecision.allowNoBilling(meta);
        }

        // Look up credit account
        CreditAccountEntity account = findAccount(userId, guestId, role);
        if (account == null) {
            return PolicyDecision.deny("credits_account_not_found");
        }

        long effectiveBalance = account.getEffectiveBalance();
        if (effectiveBalance <= 0) {
            return PolicyDecision.deny("credits_insufficient");
        }

        return PolicyDecision.allow(meta, account);
    }

    private static boolean isGuestRole(@Nullable String role) {
        return role != null && GUEST_ROLE.equalsIgnoreCase(role.trim());
    }

    @Nullable
    private AiModelMetadataEntity lookupMeta(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        var map = metadataService.findByModelIds(Set.of(modelId));
        return map.get(modelId);
    }

    @Nullable
    private CreditAccountEntity findAccount(@Nullable UUID userId, @Nullable String guestId, @Nullable String role) {
        if (userId != null) {
            return accountService.getOrCreateUserAccount(userId, role);
        }
        if (guestId != null && !guestId.isBlank()) {
            return accountService.getOrCreateGuestAccount(guestId, role);
        }
        return null;
    }
}
