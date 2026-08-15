package com.webchat.adminapi.voicechat;

import com.webchat.platformapi.ai.audio.VoiceChatProperties;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.config.SysConfigService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VoiceChatAdminControllerTest {

    @Mock
    private SysConfigService sysConfigService;

    private MockMvc mockMvc;
    private UUID adminUserId;
    private VoiceChatProperties properties;

    @BeforeEach
    void setUp() {
        properties = new VoiceChatProperties();
        properties.setEnabled(true);
        properties.setMaxDurationSeconds(60);
        properties.setSttModel("whisper-1");
        properties.setTtsModel("tts-1");
        properties.setTtsVoice("alloy");
        mockMvc = MockMvcBuilders.standaloneSetup(new VoiceChatAdminController(properties, sysConfigService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void getConfigRejectsUnauthorizedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/voice-chat/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void getConfigReturnsCurrentProperties() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/voice-chat/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxDurationSeconds").value(60))
                .andExpect(jsonPath("$.data.sttModel").value("whisper-1"));
    }

    @Test
    void updateConfigPersistsProvidedValues() throws Exception {
        mockMvc.perform(
                        admin(put("/api/v1/admin/voice-chat/config")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "enabled": false,
                                          "maxDurationSeconds": 120,
                                          "sttModel": "whisper-large",
                                          "ttsModel": "tts-hd",
                                          "ttsVoice": "nova"
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.maxDurationSeconds").value(120))
                .andExpect(jsonPath("$.data.sttModel").value("whisper-large"))
                .andExpect(jsonPath("$.data.ttsModel").value("tts-hd"))
                .andExpect(jsonPath("$.data.ttsVoice").value("nova"));

        verify(sysConfigService).set("voicechat.enabled", "false");
        verify(sysConfigService).set("voicechat.max_duration_seconds", "120");
        verify(sysConfigService).set("voicechat.stt_model", "whisper-large");
        verify(sysConfigService).set("voicechat.tts_model", "tts-hd");
        verify(sysConfigService).set("voicechat.tts_voice", "nova");
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
