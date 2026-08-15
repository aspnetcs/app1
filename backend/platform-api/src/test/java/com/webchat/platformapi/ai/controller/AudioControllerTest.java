package com.webchat.platformapi.ai.controller;

import com.webchat.platformapi.ai.audio.AiAudioService;
import com.webchat.platformapi.ai.audio.AudioController;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AudioControllerTest {

    @Test
    void speechUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        AiAudioService audioService = mock(AiAudioService.class);
        UserGroupService userGroupService = mock(UserGroupService.class);
        AudioController controller = new AudioController(
                audioService,
                userGroupService,
                true,
                false,
                2000,
                "tts-1",
                "alloy",
                "mp3",
                false
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/v1/audio/speech").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacySpeechRouteIsNotMapped() throws Exception {
        AiAudioService audioService = mock(AiAudioService.class);
        UserGroupService userGroupService = mock(UserGroupService.class);
        AudioController controller = new AudioController(
                audioService,
                userGroupService,
                true,
                false,
                2000,
                "tts-1",
                "alloy",
                "mp3",
                false
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/v1/audio/tts").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void speechRejectsWhenTtsBlockedByGroupPolicy() {
        AiAudioService audioService = mock(AiAudioService.class);
        UserGroupService userGroupService = mock(UserGroupService.class);
        UUID userId = UUID.randomUUID();
        when(userGroupService.isFeatureAllowed(userId, "tts")).thenReturn(false);
        when(userGroupService.isFeatureAllowed(userId, "voice_chat")).thenReturn(false);

        AudioController controller = new AudioController(
                audioService,
                userGroupService,
                true,
                false,
                2000,
                "tts-1",
                "alloy",
                "mp3",
                true
        );

        var response = controller.speech(userId, Map.of("input", "hello"));

        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        assertEquals("tts not allowed by group policy", response.message());
    }
}
