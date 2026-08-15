package com.webchat.adminapi.feature;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeatureConfigAdminControllerTest {

    @Mock
    private FeatureConfigAdminService featureConfigAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FeatureConfigAdminController(featureConfigAdminService)).build();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/features/audio/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(featureConfigAdminService);
    }

    @Test
    void getConfigReturnsPayload() throws Exception {
        when(featureConfigAdminService.getConfig("audio")).thenReturn(Map.of(
                "featureKey", "audio",
                "enabled", true,
                "defaultModel", "tts-1"
        ));

        mockMvc.perform(get("/api/v1/admin/features/audio/config")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                        .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.featureKey").value("audio"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.defaultModel").value("tts-1"));

        verify(featureConfigAdminService).getConfig("audio");
    }

    @Test
    void updateConfigDelegatesToService() throws Exception {
        when(featureConfigAdminService.updateConfig(eq("audio"), anyMap())).thenAnswer(invocation ->
                com.webchat.platformapi.common.api.ApiResponse.ok(Map.of(
                        "featureKey", "audio",
                        "enabled", false,
                        "defaultModel", "tts-1"
                )));

        mockMvc.perform(put("/api/v1/admin/features/audio/config")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                        .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false,
                                  "defaultModel": "tts-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.featureKey").value("audio"));

        verify(featureConfigAdminService).updateConfig(eq("audio"), anyMap());
    }
}
