package com.webchat.adminapi.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthRuntimeConfigServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OAuthProperties properties;
    private OAuthRuntimeConfigService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        properties = new OAuthProperties();
        properties.setEnabled(true);
        properties.setAllowAdminLogin(true);
        properties.setProviders(configuredProviders());
        service = new OAuthRuntimeConfigService(properties, redis, new ObjectMapper());
    }

    @Test
    void currentConfigNormalizesConfiguredProviderKeysBeforeApplyingOverrides() {
        when(valueOperations.get("oauth:admin:config")).thenReturn("""
                {"enabled":true,"allowAdminLogin":true,"providers":{"GITHUB":{"enabled":false,"allowAdminLogin":false}}}
                """);

        OAuthRuntimeConfigService.RuntimeConfig config = service.currentConfig();

        assertThat(config.providers()).containsOnlyKeys("github");
        assertThat(config.getProvider("github")).isNotNull();
        assertThat(config.getProvider("GITHUB").enabled()).isFalse();
        assertThat(config.getProvider("github").allowAdminLogin()).isFalse();
    }

    @Test
    void saveAcceptsMixedCaseOverrideKeysAndPersistsNormalizedKeys() {
        OAuthRuntimeConfigService.StoredOverrides overrides = new OAuthRuntimeConfigService.StoredOverrides(
                true,
                true,
                Map.of("GITHUB", new OAuthRuntimeConfigService.ProviderOverride(false, false))
        );

        assertDoesNotThrow(() -> service.save(overrides));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("oauth:admin:config"), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).contains("\"github\"");
        assertThat(jsonCaptor.getValue()).doesNotContain("\"GITHUB\"");
    }

    private static Map<String, OAuthProperties.Provider> configuredProviders() {
        OAuthProperties.Provider github = new OAuthProperties.Provider();
        github.setEnabled(true);
        github.setDisplayName("GitHub");
        github.setAllowAdminLogin(true);
        Map<String, OAuthProperties.Provider> providers = new LinkedHashMap<>();
        providers.put("GitHub", github);
        return providers;
    }
}
