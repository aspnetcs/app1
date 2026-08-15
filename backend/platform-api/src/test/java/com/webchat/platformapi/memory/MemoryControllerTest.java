package com.webchat.platformapi.memory;

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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemoryControllerTest {

    @Mock
    private MemoryRuntimeService memoryRuntimeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MemoryController(memoryRuntimeService)).build();
    }

    @Test
    void configRejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/v1/memory/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsRuntimePayload() throws Exception {
        UUID userId = UUID.randomUUID();
        when(memoryRuntimeService.getRuntimeConfig(eq(userId))).thenReturn(Map.of(
                "enabled", true,
                "consentRequired", true,
                "consentGranted", false,
                "summary", ""
        ));

        mockMvc.perform(get("/api/v1/memory/config")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.consentRequired").value(true))
                .andExpect(jsonPath("$.data.consentGranted").value(false));
    }

    @Test
    void consentUpdatesRuntimePayload() throws Exception {
        UUID userId = UUID.randomUUID();
        when(memoryRuntimeService.updateConsent(eq(userId), eq(true))).thenReturn(Map.of(
                "enabled", true,
                "consentRequired", true,
                "consentGranted", true,
                "summary", "test"
        ));

        mockMvc.perform(put("/api/v1/memory/consent")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.consentGranted").value(true));
    }
}
