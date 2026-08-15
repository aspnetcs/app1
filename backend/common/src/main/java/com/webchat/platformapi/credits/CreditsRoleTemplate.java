package com.webchat.platformapi.credits;

public record CreditsRoleTemplate(
        String role,
        String sourceRole,
        boolean editable,
        long periodCredits,
        String periodType,
        boolean unlimited
) {
}
