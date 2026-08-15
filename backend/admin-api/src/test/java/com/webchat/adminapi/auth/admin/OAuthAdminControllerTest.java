package com.webchat.adminapi.auth.admin;

import com.webchat.adminapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OAuthAdminControllerTest {

    @Mock
    private OAuthRuntimeConfigService runtimeConfigService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OAuthAdminController(runtimeConfigService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsMaskedProviderPayload() throws Exception {
        when(runtimeConfigService.currentConfig()).thenReturn(buildRuntimeConfig());

        mockMvc.perform(admin(get("/api/v1/admin/oauth/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.providers[0].provider").value("github"))
                .andExpect(jsonPath("$.data.providers[0].clientIdMasked").value("abc***xyz"))
                .andExpect(jsonPath("$.data.providers[0].clientSecretConfigured").value(true));
    }

    @Test
    void updatePersistsOverridesAndReturnsUpdatedConfig() throws Exception {
        OAuthRuntimeConfigService.RuntimeConfig updatedConfig = new OAuthRuntimeConfigService.RuntimeConfig(
                false,
                false,
                180,
                600,
                "https://admin.example.com",
                List.of("admin.example.com"),
                Map.of(
                        "github",
                        new OAuthRuntimeConfigService.ProviderConfig(
                                "GitHub",
                                "abc123xyz",
                                "secret",
                                "https://github.com/login/oauth/authorize",
                                "https://github.com/login/oauth/access_token",
                                "https://api.github.com/user",
                                "https://api.github.com/user/emails",
                                "read:user",
                                "github",
                                false,
                                false
                        )
                )
        );
        when(runtimeConfigService.currentConfig()).thenReturn(updatedConfig);

        mockMvc.perform(
                        admin(put("/api/v1/admin/oauth/config")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "enabled": false,
                                          "allowAdminLogin": false,
                                          "providers": {
                                            "github": {
                                              "enabled": false,
                                              "allowAdminLogin": false
                                            }
                                          }
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.allowAdminLogin").value(false))
                .andExpect(jsonPath("$.data.providers[0].enabled").value(false));

        ArgumentCaptor<OAuthRuntimeConfigService.StoredOverrides> captor =
                ArgumentCaptor.forClass(OAuthRuntimeConfigService.StoredOverrides.class);
        verify(runtimeConfigService).save(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().enabled());
        assertTrue(captor.getValue().providers().containsKey("github"));
    }

    private OAuthRuntimeConfigService.RuntimeConfig buildRuntimeConfig() {
        Map<String, OAuthRuntimeConfigService.ProviderConfig> providers = new LinkedHashMap<>();
        providers.put(
                "github",
                new OAuthRuntimeConfigService.ProviderConfig(
                        "GitHub",
                        "abc123xyz",
                        "secret",
                        "https://github.com/login/oauth/authorize",
                        "https://github.com/login/oauth/access_token",
                        "https://api.github.com/user",
                        "https://api.github.com/user/emails",
                        "read:user",
                        "github",
                        true,
                        true
                )
        );
        return new OAuthRuntimeConfigService.RuntimeConfig(
                true,
                true,
                180,
                600,
                "https://admin.example.com",
                List.of("admin.example.com"),
                providers
        );
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
