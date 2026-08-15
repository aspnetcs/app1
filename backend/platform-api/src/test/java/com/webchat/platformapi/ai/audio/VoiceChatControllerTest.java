package com.webchat.platformapi.ai.audio;

import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.credits.CreditAccountEntity;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceChatControllerTest {

    @Mock
    private VoiceChatService voiceChatService;

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VoiceChatController controller = new VoiceChatController(
                voiceChatService,
                userGroupService,
                false,
                null,
                null
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void configUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/voice-chat/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void pipelineUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.wav", "audio/wav", "data".getBytes());

        mockMvc.perform(multipart("/api/v1/voice-chat/pipeline").file(audio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyVoiceChatRouteIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/v1/voice-chat/settings"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pipelineReturnsModelNotAllowedWhenCreditsPolicyDeniesRequest() throws Exception {
        VoiceChatProperties properties = new VoiceChatProperties();
        properties.setEnabled(true);
        properties.setMaxUploadBytes(1024 * 1024);
        properties.setMaxDurationSeconds(600);
        when(voiceChatService.getProperties()).thenReturn(properties);

        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        when(creditsPolicyEvaluator.evaluate(any(), isNull(), anyString(), anyString()))
                .thenReturn(CreditsPolicyEvaluator.PolicyDecision.deny("model_not_allowed"));

        VoiceChatController controller = new VoiceChatController(
                voiceChatService,
                userGroupService,
                false,
                creditsPolicyEvaluator,
                null
        );
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.wav", "audio/wav", "data".getBytes());

        localMockMvc.perform(
                        multipart("/api/v1/voice-chat/pipeline")
                                .file(audio)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, java.util.UUID.randomUUID())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "guest")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.MODEL_NOT_ALLOWED))
                .andExpect(jsonPath("$.message").value("model_not_allowed"));

        verify(voiceChatService, never()).pipeline(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void pipelineReturnsReserveFailedWhenCreditsReservationDoesNotProduceSnapshot() throws Exception {
        VoiceChatProperties properties = new VoiceChatProperties();
        properties.setEnabled(true);
        properties.setMaxUploadBytes(1024 * 1024);
        properties.setMaxDurationSeconds(600);
        when(voiceChatService.getProperties()).thenReturn(properties);

        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        CreditsRuntimeService creditsRuntimeService = mock(CreditsRuntimeService.class);
        when(creditsPolicyEvaluator.evaluate(any(), isNull(), anyString(), anyString()))
                .thenReturn(CreditsPolicyEvaluator.PolicyDecision.allow(
                        mock(AiModelMetadataEntity.class),
                        mock(CreditAccountEntity.class)
                ));
        when(creditsRuntimeService.reserve(any(), isNull(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(null);

        VoiceChatController controller = new VoiceChatController(
                voiceChatService,
                userGroupService,
                false,
                creditsPolicyEvaluator,
                creditsRuntimeService
        );
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.wav", "audio/wav", "data".getBytes());

        localMockMvc.perform(
                        multipart("/api/v1/voice-chat/pipeline")
                                .file(audio)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, java.util.UUID.randomUUID())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CREDITS_RESERVE_FAILED))
                .andExpect(jsonPath("$.message").value("credits_reserve_failed"));

        verify(voiceChatService, never()).pipeline(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void pipelineReturnsPolicyUnavailableWhenCreditsEvaluationFails() throws Exception {
        VoiceChatProperties properties = new VoiceChatProperties();
        properties.setEnabled(true);
        properties.setMaxUploadBytes(1024 * 1024);
        properties.setMaxDurationSeconds(600);
        when(voiceChatService.getProperties()).thenReturn(properties);

        CreditsPolicyEvaluator creditsPolicyEvaluator = mock(CreditsPolicyEvaluator.class);
        when(creditsPolicyEvaluator.evaluate(any(), isNull(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("policy down"));

        VoiceChatController controller = new VoiceChatController(
                voiceChatService,
                userGroupService,
                false,
                creditsPolicyEvaluator,
                null
        );
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.wav", "audio/wav", "data".getBytes());

        localMockMvc.perform(
                        multipart("/api/v1/voice-chat/pipeline")
                                .file(audio)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, java.util.UUID.randomUUID())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CREDITS_RESERVE_FAILED))
                .andExpect(jsonPath("$.message").value("credits_policy_unavailable"));

        verify(voiceChatService, never()).pipeline(any(), any(), anyString(), anyString(), any());
    }
}
