package com.webchat.platformapi.preferences;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserPreferenceControllerTest {

    @Mock
    private UserPreferenceService preferenceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserPreferenceController(preferenceService)).build();
    }

    @Test
    void getPreferencesRejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/v1/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void updatePreferencesReturnsServicePayload() throws Exception {
        UUID userId = UUID.randomUUID();
        when(preferenceService.updatePreferences(eq(userId), anyMap())).thenReturn(Map.of(
                "defaultAgentId", "agent-1",
                "themeMode", "dark",
                "spacingVertical", "20px",
                "spacingHorizontal", "12px",
                "mcpMode", "manual"
        ));

        mockMvc.perform(put("/api/v1/preferences")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "themeMode": "dark",
                                  "spacingVertical": "20px",
                                  "spacingHorizontal": "12px",
                                  "mcpMode": "manual"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.themeMode").value("dark"))
                .andExpect(jsonPath("$.data.spacingVertical").value("20px"))
                .andExpect(jsonPath("$.data.spacingHorizontal").value("12px"))
                .andExpect(jsonPath("$.data.mcpMode").value("manual"));
    }
}
