package com.webchat.adminapi.credits;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.credits.CreditAccountEntity;
import com.webchat.platformapi.credits.CreditAccountRepository;
import com.webchat.platformapi.credits.CreditsAccountService;
import com.webchat.platformapi.credits.CreditsRoleTemplate;
import com.webchat.platformapi.credits.CreditsRoleTemplateService;
import com.webchat.platformapi.credits.CreditsSystemConfig;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditsAdminService {

    private final CreditsSystemConfig creditsSystemConfig;
    private final CreditsRoleTemplateService creditsRoleTemplateService;
    private final CreditsAccountService creditsAccountService;
    private final CreditAccountRepository creditAccountRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public CreditsAdminService(CreditsSystemConfig creditsSystemConfig,
                               CreditsRoleTemplateService creditsRoleTemplateService,
                               CreditsAccountService creditsAccountService,
                               CreditAccountRepository creditAccountRepository,
                               UserRepository userRepository,
                               JdbcTemplate jdbcTemplate) {
        this.creditsSystemConfig = creditsSystemConfig;
        this.creditsRoleTemplateService = creditsRoleTemplateService;
        this.creditsAccountService = creditsAccountService;
        this.creditAccountRepository = creditAccountRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ApiResponse<Map<String, Object>> getSystemConfig() {
        return ApiResponse.ok(buildSystemConfigPayload());
    }

    @Transactional
    public ApiResponse<Map<String, Object>> updateSystemConfig(Boolean creditsSystemEnabled,
                                                               Integer creditsPerUsd,
                                                               Boolean freeModeEnabled) {
        if (creditsSystemEnabled == null && creditsPerUsd == null && freeModeEnabled == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "at least one config field is required");
        }
        if (creditsPerUsd != null && creditsPerUsd <= 0) {
            return ApiResponse.error(ErrorCodes.PARAM_INVALID, "creditsPerUsd must be greater than 0");
        }
        if (creditsSystemEnabled != null) {
            creditsSystemConfig.setCreditsSystemEnabled(creditsSystemEnabled);
        }
        if (creditsPerUsd != null) {
            creditsSystemConfig.setCreditsPerUsd(creditsPerUsd);
        }
        if (freeModeEnabled != null) {
            creditsSystemConfig.setFreeModeEnabled(freeModeEnabled);
        }
        return ApiResponse.ok(buildSystemConfigPayload());
    }

    public ApiResponse<Map<String, Object>> listRoleTemplates() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (CreditsRoleTemplate template : creditsRoleTemplateService.listEditableTemplates()) {
            items.add(toRoleTemplatePayload(template));
        }
        items.add(toRoleTemplatePayload(creditsRoleTemplateService.resolveTemplate("pending")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("total", items.size());
        return ApiResponse.ok(payload);
    }

    @Transactional
    public ApiResponse<Map<String, Object>> updateRoleTemplate(String role,
                                                               Long periodCredits,
                                                               String periodType,
                                                               Boolean unlimited) {
        String normalizedRole = normalizeText(role);
        if (normalizedRole == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "role is required");
        }
        if (!Boolean.TRUE.equals(unlimited) && periodCredits == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "periodCredits is required");
        }
        if (periodCredits != null && periodCredits < 0 && !Boolean.TRUE.equals(unlimited)) {
            return ApiResponse.error(ErrorCodes.PARAM_INVALID, "periodCredits must be >= 0 when unlimited is false");
        }

        try {
            CreditsRoleTemplate updated = creditsRoleTemplateService.updateTemplate(
                    normalizedRole,
                    periodCredits == null ? 0L : periodCredits,
                    periodType,
                    Boolean.TRUE.equals(unlimited)
            );
            return ApiResponse.ok(Map.of("template", toRoleTemplatePayload(updated)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_INVALID, e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getUserAccountDetail(UUID userId) {
        Optional<UserEntity> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "user not found");
        }

        UserEntity user = userOptional.get();
        CreditAccountEntity account = creditsSystemConfig.isCreditsSystemEnabled()
                ? creditsAccountService.getOrCreateUserAccount(userId, user.getRole())
                : creditAccountRepository.findByUserId(userId).orElse(null);
        CreditsRoleTemplate roleTemplate = creditsRoleTemplateService.resolveTemplate(user.getRole());

        return ApiResponse.ok(buildAccountDetailPayload(user, account, roleTemplate, null));
    }

    @Transactional
    public ApiResponse<Map<String, Object>> adjustUserAccount(UUID userId, Long deltaCredits, String reason) {
        if (deltaCredits == null || deltaCredits == 0L) {
            return ApiResponse.error(ErrorCodes.PARAM_INVALID, "deltaCredits must not be 0");
        }

        Optional<UserEntity> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "user not found");
        }

        UserEntity user = userOptional.get();
        CreditAccountEntity account = creditsAccountService.getOrCreateUserAccount(userId, user.getRole());
        if (account == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "credit account not available");
        }

        account.setManualCreditAdjustment(account.getManualCreditAdjustment() + deltaCredits);
        account.setCreditBalance(account.getCreditBalance() + deltaCredits);
        CreditAccountEntity saved = creditAccountRepository.save(account);
        CreditsRoleTemplate template = creditsRoleTemplateService.resolveTemplate(user.getRole());

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "adjust");
        action.put("deltaCredits", deltaCredits);
        action.put("reason", sanitizeReason(reason));

        return ApiResponse.ok(buildAccountDetailPayload(user, saved, template, action));
    }

    @Transactional
    public ApiResponse<Map<String, Object>> resetUserAccount(UUID userId, String roleOverride) {
        Optional<UserEntity> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "user not found");
        }

        UserEntity user = userOptional.get();
        String appliedRole = resolveAppliedRole(user, roleOverride);
        CreditAccountEntity account = creditsAccountService.getOrCreateUserAccount(userId, appliedRole);
        if (account == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "credit account not available");
        }
        CreditAccountEntity saved = creditsAccountService.syncAccount(account, appliedRole, true);
        CreditsRoleTemplate template = creditsRoleTemplateService.resolveTemplate(appliedRole);

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "reset");
        action.put("appliedRole", appliedRole);
        action.put("forceResetPeriod", true);

        return ApiResponse.ok(buildAccountDetailPayload(user, saved, template, action));
    }

    @Transactional
    public ApiResponse<Map<String, Object>> grantOrSyncUserAccount(UUID userId, String roleOverride, boolean forceResetPeriod) {
        Optional<UserEntity> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "user not found");
        }

        UserEntity user = userOptional.get();
        String appliedRole = resolveAppliedRole(user, roleOverride);
        CreditAccountEntity account = creditsAccountService.getOrCreateUserAccount(userId, appliedRole);
        if (account == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "credit account not available");
        }
        CreditAccountEntity saved = forceResetPeriod
                ? creditsAccountService.syncAccount(account, appliedRole, true)
                : account;
        CreditsRoleTemplate template = creditsRoleTemplateService.resolveTemplate(appliedRole);

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "grant-sync");
        action.put("appliedRole", appliedRole);
        action.put("forceResetPeriod", forceResetPeriod);

        return ApiResponse.ok(buildAccountDetailPayload(user, saved, template, action));
    }

    public ApiResponse<Map<String, Object>> listSettlements(int page,
                                                            int size,
                                                            String userIdFilter,
                                                            String guestIdFilter,
                                                            String requestIdFilter,
                                                            String settlementStatusFilter,
                                                            String modelIdFilter) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int offset = safePage * safeSize;

        UUID parsedUserId = null;
        String normalizedUserId = normalizeText(userIdFilter);
        if (normalizedUserId != null) {
            try {
                parsedUserId = UUID.fromString(normalizedUserId);
            } catch (IllegalArgumentException e) {
                return ApiResponse.error(ErrorCodes.PARAM_INVALID, "userId must be a valid UUID");
            }
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (parsedUserId != null) {
            where.append(" AND s.user_id = ? ");
            args.add(parsedUserId);
        }
        String normalizedGuestId = normalizeText(guestIdFilter);
        if (normalizedGuestId != null) {
            where.append(" AND s.guest_id = ? ");
            args.add(normalizedGuestId);
        }
        String normalizedRequestId = normalizeText(requestIdFilter);
        if (normalizedRequestId != null) {
            where.append(" AND s.request_id = ? ");
            args.add(normalizedRequestId);
        }
        String normalizedStatus = normalizeText(settlementStatusFilter);
        if (normalizedStatus != null) {
            where.append(" AND s.settlement_status = ? ");
            args.add(normalizedStatus.toLowerCase(Locale.ROOT));
        }
        String normalizedModelId = normalizeText(modelIdFilter);
        if (normalizedModelId != null) {
            where.append(" AND s.model_id = ? ");
            args.add(normalizedModelId);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM credit_settlement_snapshots s" + where,
                args.toArray(),
                Long.class
        );

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT
                            s.id,
                            s.request_id,
                            s.user_id,
                            s.guest_id,
                            s.role_snapshot,
                            s.model_id,
                            s.model_name_snapshot,
                            s.free_mode_snapshot,
                            s.billing_enabled_snapshot,
                            s.credits_per_usd_snapshot,
                            s.request_price_usd_snapshot,
                            s.prompt_price_usd_snapshot,
                            s.input_price_usd_per_1m_snapshot,
                            s.output_price_usd_per_1m_snapshot,
                            s.request_count,
                            s.prompt_count,
                            s.input_tokens,
                            s.output_tokens,
                            s.request_cost_usd,
                            s.prompt_cost_usd,
                            s.input_cost_usd,
                            s.output_cost_usd,
                            s.reserved_credits,
                            s.settled_credits,
                            s.refunded_credits,
                            s.settlement_status,
                            s.failure_reason,
                            s.started_at,
                            s.settled_at,
                            s.created_at,
                            u.email AS user_email,
                            u.phone AS user_phone,
                            u.role AS current_user_role
                        FROM credit_settlement_snapshots s
                        LEFT JOIN users u ON u.id = s.user_id
                        """
                        + where +
                        " ORDER BY s.created_at DESC LIMIT ? OFFSET ?",
                listArgs.toArray()
        );

        List<Map<String, Object>> items = rows.stream().map(this::toSettlementPayload).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("total", total == null ? 0L : total);
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("filters", Map.of(
                "userId", normalizedUserId == null ? "" : normalizedUserId,
                "guestId", normalizedGuestId == null ? "" : normalizedGuestId,
                "requestId", normalizedRequestId == null ? "" : normalizedRequestId,
                "settlementStatus", normalizedStatus == null ? "" : normalizedStatus,
                "modelId", normalizedModelId == null ? "" : normalizedModelId
        ));
        payload.put("pageSummary", buildSettlementSummary(rows));
        return ApiResponse.ok(payload);
    }

    private Map<String, Object> buildSystemConfigPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("creditsSystemEnabled", creditsSystemConfig.isCreditsSystemEnabled());
        payload.put("creditsPerUsd", creditsSystemConfig.getCreditsPerUsd());
        payload.put("freeModeEnabled", creditsSystemConfig.isFreeModeEnabled());
        return payload;
    }

    private Map<String, Object> buildAccountDetailPayload(UserEntity user,
                                                          CreditAccountEntity account,
                                                          CreditsRoleTemplate roleTemplate,
                                                          Map<String, Object> action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", toUserPayload(user));
        payload.put("template", toRoleTemplatePayload(roleTemplate));
        payload.put("account", toAccountPayload(account));
        if (action != null) {
            payload.put("action", action);
        }
        return payload;
    }

    private Map<String, Object> toUserPayload(UserEntity user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("phone", defaultString(user.getPhone()));
        payload.put("email", defaultString(user.getEmail()));
        payload.put("avatar", defaultString(user.getAvatar()));
        payload.put("role", defaultString(user.getRole()));
        payload.put("status", user.getDeletedAt() == null ? "active" : "disabled");
        payload.put("createdAt", user.getCreatedAt());
        payload.put("updatedAt", user.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> toRoleTemplatePayload(CreditsRoleTemplate template) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", template.role());
        payload.put("sourceRole", template.sourceRole());
        payload.put("editable", template.editable());
        payload.put("periodCredits", template.periodCredits());
        payload.put("periodType", template.periodType());
        payload.put("unlimited", template.unlimited());
        return payload;
    }

    private Map<String, Object> toAccountPayload(CreditAccountEntity account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (account == null) {
            payload.put("exists", false);
            payload.put("creditBalance", 0L);
            payload.put("creditUsed", 0L);
            payload.put("manualCreditAdjustment", 0L);
            payload.put("periodCredits", 0L);
            payload.put("periodType", "monthly");
            payload.put("unlimited", false);
            payload.put("effectiveBalance", 0L);
            payload.put("periodStartAt", null);
            payload.put("periodEndAt", null);
            payload.put("lastResetAt", null);
            payload.put("createdAt", null);
            payload.put("updatedAt", null);
            payload.put("roleSnapshot", null);
            return payload;
        }

        payload.put("exists", true);
        payload.put("roleSnapshot", account.getRoleSnapshot());
        payload.put("creditBalance", account.getCreditBalance());
        payload.put("creditUsed", account.getCreditUsed());
        payload.put("manualCreditAdjustment", account.getManualCreditAdjustment());
        payload.put("periodCredits", account.getPeriodCredits());
        payload.put("periodType", account.getPeriodType());
        payload.put("unlimited", account.isUnlimitedPeriodCredits());
        payload.put("effectiveBalance", account.getEffectiveBalance());
        payload.put("periodStartAt", account.getPeriodStartAt());
        payload.put("periodEndAt", account.getPeriodEndAt());
        payload.put("lastResetAt", account.getLastResetAt());
        payload.put("createdAt", account.getCreatedAt());
        payload.put("updatedAt", account.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> toSettlementPayload(Map<String, Object> row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.get("id"));
        payload.put("requestId", row.get("request_id"));
        payload.put("status", row.get("settlement_status"));
        payload.put("failureReason", row.get("failure_reason"));
        payload.put("startedAt", row.get("started_at"));
        payload.put("settledAt", row.get("settled_at"));
        payload.put("createdAt", row.get("created_at"));

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("userId", row.get("user_id"));
        actor.put("guestId", row.get("guest_id"));
        actor.put("roleSnapshot", row.get("role_snapshot"));
        actor.put("userEmail", defaultString(row.get("user_email")));
        actor.put("userPhone", defaultString(row.get("user_phone")));
        actor.put("currentUserRole", defaultString(row.get("current_user_role")));
        payload.put("actor", actor);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("modelId", row.get("model_id"));
        model.put("modelName", row.get("model_name_snapshot"));
        payload.put("model", model);

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("freeMode", row.get("free_mode_snapshot"));
        billing.put("billingEnabled", row.get("billing_enabled_snapshot"));
        billing.put("creditsPerUsd", row.get("credits_per_usd_snapshot"));
        billing.put("requestPriceUsd", row.get("request_price_usd_snapshot"));
        billing.put("promptPriceUsd", row.get("prompt_price_usd_snapshot"));
        billing.put("inputPriceUsdPer1M", row.get("input_price_usd_per_1m_snapshot"));
        billing.put("outputPriceUsdPer1M", row.get("output_price_usd_per_1m_snapshot"));
        payload.put("billing", billing);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("requestCount", row.get("request_count"));
        usage.put("promptCount", row.get("prompt_count"));
        usage.put("inputTokens", row.get("input_tokens"));
        usage.put("outputTokens", row.get("output_tokens"));
        usage.put("requestCostUsd", row.get("request_cost_usd"));
        usage.put("promptCostUsd", row.get("prompt_cost_usd"));
        usage.put("inputCostUsd", row.get("input_cost_usd"));
        usage.put("outputCostUsd", row.get("output_cost_usd"));
        payload.put("usage", usage);

        Map<String, Object> credits = new LinkedHashMap<>();
        credits.put("reservedCredits", row.get("reserved_credits"));
        credits.put("settledCredits", row.get("settled_credits"));
        credits.put("refundedCredits", row.get("refunded_credits"));
        payload.put("credits", credits);

        return payload;
    }

    private Map<String, Object> buildSettlementSummary(List<Map<String, Object>> rows) {
        long reservedCredits = 0L;
        long settledCredits = 0L;
        long refundedCredits = 0L;
        BigDecimal requestCostUsd = BigDecimal.ZERO;
        BigDecimal promptCostUsd = BigDecimal.ZERO;
        BigDecimal inputCostUsd = BigDecimal.ZERO;
        BigDecimal outputCostUsd = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            reservedCredits += toLong(row.get("reserved_credits"));
            settledCredits += toLong(row.get("settled_credits"));
            refundedCredits += toLong(row.get("refunded_credits"));
            requestCostUsd = requestCostUsd.add(toBigDecimal(row.get("request_cost_usd")));
            promptCostUsd = promptCostUsd.add(toBigDecimal(row.get("prompt_cost_usd")));
            inputCostUsd = inputCostUsd.add(toBigDecimal(row.get("input_cost_usd")));
            outputCostUsd = outputCostUsd.add(toBigDecimal(row.get("output_cost_usd")));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reservedCredits", reservedCredits);
        summary.put("settledCredits", settledCredits);
        summary.put("refundedCredits", refundedCredits);
        summary.put("requestCostUsd", requestCostUsd);
        summary.put("promptCostUsd", promptCostUsd);
        summary.put("inputCostUsd", inputCostUsd);
        summary.put("outputCostUsd", outputCostUsd);
        return summary;
    }

    private String resolveAppliedRole(UserEntity user, String roleOverride) {
        String normalizedRoleOverride = normalizeText(roleOverride);
        if (normalizedRoleOverride != null) {
            return normalizedRoleOverride;
        }
        return defaultString(user.getRole(), "user");
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String defaultString(Object value) {
        return defaultString(value, "");
    }

    private static String defaultString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String sanitizeReason(String reason) {
        String normalized = normalizeText(reason);
        if (normalized == null) {
            return "";
        }
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }
}
