package com.webchat.platformapi.credits;

import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.config.SysConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CreditsRoleTemplateService {

    private static final List<String> DEFAULT_EDITABLE_ROLES = List.of("admin", "premium", "user", "guest");

    private final RolePolicyProperties rolePolicyProperties;
    private final SysConfigService sysConfigService;

    public CreditsRoleTemplateService(RolePolicyProperties rolePolicyProperties,
                                      SysConfigService sysConfigService) {
        this.rolePolicyProperties = rolePolicyProperties;
        this.sysConfigService = sysConfigService;
    }

    public CreditsRoleTemplate resolveTemplate(String role) {
        String requestedRole = normalizeRequestedRole(role);
        String sourceRole = normalizeSourceRole(requestedRole);
        RolePolicyProperties.RoleConfig defaults = rolePolicyProperties.getPolicy(sourceRole);
        long defaultCredits = defaults.getPeriodCredits();
        String defaultPeriodType = normalizePeriodType(defaults.getPeriodType());
        long periodCredits = sysConfigService.getLong(periodCreditsKey(sourceRole), defaultCredits);
        String periodType = normalizePeriodType(sysConfigService.getOrDefault(periodTypeKey(sourceRole), defaultPeriodType));
        return new CreditsRoleTemplate(
                requestedRole,
                sourceRole,
                !"pending".equals(requestedRole),
                periodCredits,
                periodType,
                periodCredits < 0
        );
    }

    public List<CreditsRoleTemplate> listEditableTemplates() {
        Set<String> roles = new LinkedHashSet<>(DEFAULT_EDITABLE_ROLES);
        roles.addAll(rolePolicyProperties.getPolicies().keySet().stream()
                .map(this::normalizeRequestedRole)
                .filter(role -> !"pending".equals(role))
                .toList());
        List<CreditsRoleTemplate> templates = new ArrayList<>(roles.size());
        for (String role : roles) {
            templates.add(resolveTemplate(role));
        }
        return templates;
    }

    public CreditsRoleTemplate updateTemplate(String role, long periodCredits, String periodType, boolean unlimited) {
        String editableRole = normalizeRequestedRole(role);
        if ("pending".equals(editableRole)) {
            throw new IllegalArgumentException("pending role template is read-only");
        }
        long storedCredits = unlimited ? -1L : Math.max(0L, periodCredits);
        String normalizedPeriodType = normalizePeriodType(periodType);
        sysConfigService.set(periodCreditsKey(editableRole), String.valueOf(storedCredits));
        sysConfigService.set(periodTypeKey(editableRole), normalizedPeriodType);
        return resolveTemplate(editableRole);
    }

    public String normalizeRequestedRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeSourceRole(String role) {
        return "pending".equals(normalizeRequestedRole(role)) ? "user" : normalizeRequestedRole(role);
    }

    public String normalizePeriodType(String periodType) {
        if (periodType == null || periodType.isBlank()) {
            return "monthly";
        }
        String normalized = periodType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "daily", "weekly", "monthly", "none" -> normalized;
            default -> "monthly";
        };
    }

    private static String periodCreditsKey(String role) {
        return "credits.role." + role + ".periodCredits";
    }

    private static String periodTypeKey(String role) {
        return "credits.role." + role + ".periodType";
    }
}
