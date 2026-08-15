package com.webchat.platformapi.onboarding;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

    @Mock
    private OnboardingService onboardingService;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(onboardingService)).build();
        userId = UUID.randomUUID();
    }

    @Test
    void onboardingRoutesRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(put("/api/v1/onboarding/state").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void stateRouteReturnsCurrentPayload() throws Exception {
        when(onboardingService.getState(userId)).thenReturn(Map.of(
                "status", "in_progress",
                "currentStep", "market",
                "completedSteps", List.of("welcome", "history"),
                "shouldShow", true
        ));

        mockMvc.perform(get("/api/v1/onboarding/state").requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.currentStep").value("market"))
                .andExpect(jsonPath("$.data.completedSteps[0]").value("welcome"));

        verify(onboardingService).getState(userId);
    }

    @Test
    void updateRouteSurfacesValidationErrors() throws Exception {
        when(onboardingService.updateState(userId, Map.of("status", "bad")))
                .thenThrow(new IllegalArgumentException("status is invalid"));

        mockMvc.perform(
                        put("/api/v1/onboarding/state")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"bad\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("status is invalid"));
    }
}