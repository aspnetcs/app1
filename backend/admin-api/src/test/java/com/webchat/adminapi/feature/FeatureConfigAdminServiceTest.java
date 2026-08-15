package com.webchat.adminapi.feature;

import com.webchat.platformapi.config.SysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureConfigAdminServiceTest {

    @Test
    void getConfigMergesPersistedValueAndEnvironmentDefault() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.audio.tts.default-model", "tts-1")
                .withProperty("platform.audio.tts.browser-fallback-enabled", "true");
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.get(anyString())).thenReturn(Optional.empty());
        when(sysConfigService.get("platform.audio.tts.enabled")).thenReturn(Optional.of("true"));

        FeatureConfigAdminService service = new FeatureConfigAdminService(environment, sysConfigService);

        Map<String, Object> config = service.getConfig("audio");

        assertThat(config.get("featureKey")).isEqualTo("audio");
        assertThat(config.get("enabled")).isEqualTo(true);
        assertThat(config.get("defaultModel")).isEqualTo("tts-1");
        assertThat(config.get("browserFallbackEnabled")).isEqualTo(true);
    }

    @Test
    void updateConfigPersistsNormalizedListValues() {
        MockEnvironment environment = new MockEnvironment();
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.get(anyString())).thenReturn(Optional.empty());

        FeatureConfigAdminService service = new FeatureConfigAdminService(environment, sysConfigService);

        service.updateConfig("web-read", Map.of(
                "enabled", true,
                "allowedHosts", List.of("example.com", "docs.example.com", "example.com")
        ));

        verify(sysConfigService).set("platform.web-read.enabled", "true");
        verify(sysConfigService).set("platform.web-read.allowed-hosts", "example.com,docs.example.com");
    }

    @Test
    void invalidNumberIsRejected() {
        MockEnvironment environment = new MockEnvironment();
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.get(anyString())).thenReturn(Optional.empty());
        FeatureConfigAdminService service = new FeatureConfigAdminService(environment, sysConfigService);

        var response = service.updateConfig("multi-chat", Map.of("maxModels", 0));

        assertThat(response.code()).isNotEqualTo(0);
        assertThat(response.message()).contains("maxModels");
    }
}
