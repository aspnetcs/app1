package com.webchat.adminapi.onboarding;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OnboardingAdminControllerTest {

    @Mock
    private OnboardingAdminService onboardingAdminService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingAdminController(onboardingAdminService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/onboarding/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configRoundTripReturnsPayload() throws Exception {
        when(onboardingAdminService.getConfig()).thenReturn(Map.of(
                "enabled", true,
                "allowSkip", true,
                "welcomeTitle", "欢迎使用 AI App",
                "steps", List.of("welcome", "history")
        ));

        mockMvc.perform(admin(get("/api/v1/admin/onboarding/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.welcomeTitle").value("欢迎使用 AI App"));

        verify(onboardingAdminService).getConfig();
    }

    @Test
    void resetRejectsInvalidUserId() throws Exception {
        mockMvc.perform(
                        admin(post("/api/v1/admin/onboarding/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"bad-uuid\"}"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("userId is invalid"));
    }

    @Test
    void updateConfigDelegatesToService() throws Exception {
        when(onboardingAdminService.updateConfig(Map.of("enabled", false))).thenReturn(Map.of("enabled", false));

        mockMvc.perform(
                        admin(put("/api/v1/admin/onboarding/config")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":false}"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(onboardingAdminService).updateConfig(Map.of("enabled", false));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}