package com.webchat.platformapi.auth.role;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-role policy configuration.
 * Binds from platform.role-policy.policies.{roleName} in application.yml.
 * Follows the same Map-based pattern as OAuthProperties.providers.
 */
@ConfigurationProperties(prefix = "platform.role-policy")
public class RolePolicyProperties {

    private Map<String, RoleConfig> policies = new LinkedHashMap<>();

    public Map<String, RoleConfig> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, RoleConfig> policies) {
        this.policies = policies == null ? new LinkedHashMap<>() : policies;
    }

    /**
     * Returns the policy for the given role, falling back to "user" defaults
     * if the role key is not configured.
     */
    public RoleConfig getPolicy(String role) {
        if (role == null || role.isBlank()) {
            return getOrDefault("user");
        }
        RoleConfig config = policies.get(role.trim().toLowerCase());
        return config != null ? config : getOrDefault("user");
    }

    private RoleConfig getOrDefault(String key) {
        RoleConfig config = policies.get(key);
        return config != null ? config : new RoleConfig();
    }

    public static class RoleConfig {
        /** Comma-separated model IDs. Empty = all models visible. */
        private String allowedModels = "";
        /** Per-user chat requests per minute. */
        private int rateLimitPerMinute = 30;
        /** Per-user per-model requests per minute. 0 = disabled. */
        private int modelRateLimitPerMinute = 0;
        /** Daily successful chat count limit. 0 = unlimited. */
        private int dailyChatQuota = 0;
        /** Credits granted per period. 0 = no credits. -1 = unlimited. */
        private long periodCredits = 0;
        /** Period type: daily, weekly, monthly, none. */
        private String periodType = "monthly";
        /** Per-user requests per minute (credits-aware). */
        private int rpmLimit = 30;
        /** Per-user per-model requests per minute (credits-aware). */
        private int modelRpmLimit = 0;

        public String getAllowedModels() {
            return allowedModels;
        }

        public void setAllowedModels(String allowedModels) {
            this.allowedModels = allowedModels == null ? "" : allowedModels;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(int rateLimitPerMinute) {
            this.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
        }

        public int getModelRateLimitPerMinute() {
            return modelRateLimitPerMinute;
        }

        public void setModelRateLimitPerMinute(int modelRateLimitPerMinute) {
            this.modelRateLimitPerMinute = Math.max(0, modelRateLimitPerMinute);
        }

        public int getDailyChatQuota() {
            return dailyChatQuota;
        }

        public void setDailyChatQuota(int dailyChatQuota) {
            this.dailyChatQuota = Math.max(0, dailyChatQuota);
        }

        public long getPeriodCredits() {
            return periodCredits;
        }

        public void setPeriodCredits(long periodCredits) {
            this.periodCredits = periodCredits;
        }

        public String getPeriodType() {
            return periodType;
        }

        public void setPeriodType(String periodType) {
            this.periodType = periodType == null ? "monthly" : periodType;
        }

        public int getRpmLimit() {
            return rpmLimit;
        }

        public void setRpmLimit(int rpmLimit) {
            this.rpmLimit = Math.max(0, rpmLimit);
        }

        public int getModelRpmLimit() {
            return modelRpmLimit;
        }

        public void setModelRpmLimit(int modelRpmLimit) {
            this.modelRpmLimit = Math.max(0, modelRpmLimit);
        }

        public boolean isUnlimited() {
            return periodCredits < 0;
        }
    }
}
