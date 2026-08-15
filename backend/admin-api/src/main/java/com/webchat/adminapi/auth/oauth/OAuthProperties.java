package com.webchat.adminapi.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.oauth")
public class OAuthProperties {

    private boolean enabled;
    private int stateTtlSeconds = 600;
    private int ticketTtlSeconds = 120;
    private boolean allowAdminLogin = true;
    private String callbackBaseUrl;
    private List<String> allowedRedirectHosts = new ArrayList<>();
    private Map<String, Provider> providers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public void setStateTtlSeconds(int stateTtlSeconds) {
        this.stateTtlSeconds = stateTtlSeconds;
    }

    public int getTicketTtlSeconds() {
        return ticketTtlSeconds;
    }

    public void setTicketTtlSeconds(int ticketTtlSeconds) {
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    public boolean isAllowAdminLogin() {
        return allowAdminLogin;
    }

    public void setAllowAdminLogin(boolean allowAdminLogin) {
        this.allowAdminLogin = allowAdminLogin;
    }

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public List<String> getAllowedRedirectHosts() {
        return allowedRedirectHosts;
    }

    public void setAllowedRedirectHosts(List<String> allowedRedirectHosts) {
        this.allowedRedirectHosts = allowedRedirectHosts == null ? new ArrayList<>() : allowedRedirectHosts;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers == null ? new LinkedHashMap<>() : providers;
    }

    public Provider getProvider(String providerKey) {
        if (providerKey == null) return null;
        return providers.get(providerKey.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isProviderEnabledForScene(String providerKey, String scene) {
        if (!enabled) return false;
        Provider provider = getProvider(providerKey);
        if (provider == null || !provider.isEnabled()) return false;
        return "admin".equalsIgnoreCase(scene) && allowAdminLogin && provider.isAllowAdminLogin();
    }

    public static class Provider {
        private boolean enabled;
        private String displayName;
        private String clientId;
        private String clientSecret;
        private String authorizeUri;
        private String tokenUri;
        private String userInfoUri;
        private String emailUri;
        private String scope;
        private String icon;
        private boolean allowAdminLogin = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAuthorizeUri() {
            return authorizeUri;
        }

        public void setAuthorizeUri(String authorizeUri) {
            this.authorizeUri = authorizeUri;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getUserInfoUri() {
            return userInfoUri;
        }

        public void setUserInfoUri(String userInfoUri) {
            this.userInfoUri = userInfoUri;
        }

        public String getEmailUri() {
            return emailUri;
        }

        public void setEmailUri(String emailUri) {
            this.emailUri = emailUri;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public boolean isAllowAdminLogin() {
            return allowAdminLogin;
        }

        public void setAllowAdminLogin(boolean allowAdminLogin) {
            this.allowAdminLogin = allowAdminLogin;
        }
    }
}


