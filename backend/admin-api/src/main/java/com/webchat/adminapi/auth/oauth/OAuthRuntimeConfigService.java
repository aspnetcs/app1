package com.webchat.adminapi.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.oauth.OAuthConfigSupport;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OAuthRuntimeConfigService {

    private static final String REDIS_KEY = "oauth:admin:config";

    private final OAuthProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public OAuthRuntimeConfigService(OAuthProperties properties, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.properties = properties;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public RuntimeConfig currentConfig() {
        StoredOverrides overrides = readOverrides();
        Map<String, OAuthProperties.Provider> configuredProviders = configuredProviders();
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        for (Map.Entry<String, OAuthProperties.Provider> entry : configuredProviders.entrySet()) {
            String key = entry.getKey();
            OAuthProperties.Provider source = entry.getValue();
            ProviderOverride providerOverride = overrides.providers() == null ? null : overrides.providers().get(key);
            providers.put(key, new ProviderConfig(
                    OAuthConfigSupport.firstNonBlank(source.getDisplayName(), OAuthConfigSupport.capitalize(key)),
                    source.getClientId(),
                    source.getClientSecret(),
                    source.getAuthorizeUri(),
                    source.getTokenUri(),
                    source.getUserInfoUri(),
                    source.getEmailUri(),
                    source.getScope(),
                    OAuthConfigSupport.firstNonBlank(source.getIcon(), key),
                    providerOverride != null && providerOverride.enabled() != null ? providerOverride.enabled() : source.isEnabled(),
                    providerOverride != null && providerOverride.allowAdminLogin() != null ? providerOverride.allowAdminLogin() : source.isAllowAdminLogin()
            ));
        }
        return new RuntimeConfig(
                overrides.enabled() != null ? overrides.enabled() : properties.isEnabled(),
                overrides.allowAdminLogin() != null ? overrides.allowAdminLogin() : properties.isAllowAdminLogin(),
                properties.getStateTtlSeconds(),
                properties.getTicketTtlSeconds(),
                properties.getCallbackBaseUrl(),
                properties.getAllowedRedirectHosts(),
                providers
        );
    }

    public void save(StoredOverrides overrides) throws OAuthConfigException {
        if (overrides == null) {
            throw new OAuthConfigException("oauth config payload missing");
        }
        Map<String, ProviderOverride> providers = overrides.providers() == null ? Map.of() : overrides.providers();
        Map<String, OAuthProperties.Provider> configuredProviders = configuredProviders();
        for (String key : providers.keySet()) {
            if (!configuredProviders.containsKey(OAuthConfigSupport.normalizeKey(key))) {
                throw new OAuthConfigException("unknown provider: " + key);
            }
        }
        StoredOverrides normalized = new StoredOverrides(
                overrides.enabled(),
                overrides.allowAdminLogin(),
                OAuthConfigSupport.normalizeKeys(providers)
        );
        try {
            redis.opsForValue().set(REDIS_KEY, objectMapper.writeValueAsString(normalized));
        } catch (Exception e) {
            throw new OAuthConfigException("save oauth config failed", e);
        }
    }

    private StoredOverrides readOverrides() {
        try {
            String json = redis.opsForValue().get(REDIS_KEY);
            if (json == null || json.isBlank()) {
                return new StoredOverrides(null, null, Map.of());
            }
            StoredOverrides overrides = objectMapper.readValue(json, StoredOverrides.class);
            return overrides == null ? new StoredOverrides(null, null, Map.of()) : new StoredOverrides(
                    overrides.enabled(),
                    overrides.allowAdminLogin(),
                    OAuthConfigSupport.normalizeKeys(overrides.providers() == null ? Map.of() : overrides.providers())
            );
        } catch (Exception e) {
            return new StoredOverrides(null, null, Map.of());
        }
    }

    private Map<String, OAuthProperties.Provider> configuredProviders() {
        return OAuthConfigSupport.normalizeKeys(properties.getProviders());
    }

    public record RuntimeConfig(
            boolean enabled,
            boolean allowAdminLogin,
            int stateTtlSeconds,
            int ticketTtlSeconds,
            String callbackBaseUrl,
            List<String> allowedRedirectHosts,
            Map<String, ProviderConfig> providers
    ) {
        public ProviderConfig getProvider(String providerKey) {
            if (providerKey == null) return null;
            return providers.get(OAuthConfigSupport.normalizeKey(providerKey));
        }

        public boolean isProviderEnabledForScene(String providerKey, String scene) {
            if (!enabled) return false;
            ProviderConfig provider = getProvider(providerKey);
            if (provider == null || !provider.enabled()) return false;
            return "admin".equalsIgnoreCase(scene) && allowAdminLogin && provider.allowAdminLogin();
        }
    }

    public record ProviderConfig(
            String displayName,
            String clientId,
            String clientSecret,
            String authorizeUri,
            String tokenUri,
            String userInfoUri,
            String emailUri,
            String scope,
            String icon,
            boolean enabled,
            boolean allowAdminLogin
    ) {
    }

    public record StoredOverrides(
            Boolean enabled,
            Boolean allowAdminLogin,
            Map<String, ProviderOverride> providers
    ) {
    }

    public record ProviderOverride(
            Boolean enabled,
            Boolean allowAdminLogin
    ) {
    }

    public static class OAuthConfigException extends Exception {
        public OAuthConfigException(String message) {
            super(message);
        }

        public OAuthConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


