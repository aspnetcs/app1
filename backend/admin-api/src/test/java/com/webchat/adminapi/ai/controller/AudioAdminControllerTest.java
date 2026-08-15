package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AudioAdminControllerTest {

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AudioAdminController(true, true, true, "tts-1", "alloy", "mp3", 2000)
        ).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void ttsConfigRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audio/tts-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void ttsConfigReturnsCurrentFeaturePayload() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/audio/tts-config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.featureKey").value("platform.audio.tts.enabled"))
                .andExpect(jsonPath("$.data.defaultModel").value("tts-1"))
                .andExpect(jsonPath("$.data.defaultVoice").value("alloy"))
                .andExpect(jsonPath("$.data.responseFormat").value("mp3"))
                .andExpect(jsonPath("$.data.maxInputChars").value(2000))
                .andExpect(jsonPath("$.data.browserFallbackEnabled").value(true));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
