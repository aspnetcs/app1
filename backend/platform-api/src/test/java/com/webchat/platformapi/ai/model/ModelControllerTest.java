package com.webchat.platformapi.ai.model;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ModelControllerTest {

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private AiModelMetadataService metadataService;

    @Mock
    private RolePolicyService rolePolicyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ModelController controller = new ModelController(
                channelRouter,
                metadataService,
                new AiModelCapabilityResolver(),
                rolePolicyService,
                null,
                true
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listUsesRoutableChannelsAndReturnsSortedMetadataCatalog() throws Exception {
        UUID userId = UUID.randomUUID();
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o, claude-3-5-sonnet");
        channel.setExtraConfig(Map.of(
                "modelMeta",
                Map.of(
                        "gpt-4o",
                        Map.of(
                                "name", "Channel GPT-4o",
                                "avatar", "channel-openai",
                                "description", "channel description"
                        )
                )
        ));

        AiModelMetadataEntity claude = new AiModelMetadataEntity();
        claude.setModelId("claude-3-5-sonnet");
        claude.setName("Claude 3.5 Sonnet");
        claude.setPinned(true);
        claude.setSortOrder(20);

        AiModelMetadataEntity gpt = new AiModelMetadataEntity();
        gpt.setModelId("gpt-4o");
        gpt.setName("GPT 4o");
        gpt.setAvatar("custom-openai");
        gpt.setDescription("Latest OpenAI model");
        gpt.setSortOrder(1);
        gpt.setDefaultSelected(true);
        gpt.setMultiChatEnabled(false);
        gpt.setImageParsingOverride(false);

        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));
        when(metadataService.findByModelIds(anyCollection())).thenReturn(Map.of(
                "claude-3-5-sonnet", claude,
                "gpt-4o", gpt
        ));

        mockMvc.perform(
                        get("/api/v1/models")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("claude-3-5-sonnet"))
                .andExpect(jsonPath("$.data[0].name").value("Claude 3.5 Sonnet"))
                .andExpect(jsonPath("$.data[0].avatar").value("claude"))
                .andExpect(jsonPath("$.data[1].id").value("gpt-4o"))
                .andExpect(jsonPath("$.data[1].name").value("GPT 4o"))
                .andExpect(jsonPath("$.data[1].avatar").value("custom-openai"))
                .andExpect(jsonPath("$.data[1].description").value("Latest OpenAI model"))
                .andExpect(jsonPath("$.data[1].multiChatEnabled").value(false))
                .andExpect(jsonPath("$.data[1].defaultSelected").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].supportsImageParsing").value(false))
                .andExpect(jsonPath("$.data[1].supportsImageParsingSource").value("manual"))
                .andExpect(jsonPath("$.data[0].supportsImageParsing").value(true))
                .andExpect(jsonPath("$.data[0].supportsImageParsingSource").value("inferred"));

        verify(metadataService).findByModelIds(argThat(ids ->
                ids.size() == 2
                        && ids.contains("gpt-4o")
                        && ids.contains("claude-3-5-sonnet")
        ));
    }

    @Test
    void listIncludesMappedModelIdsFromRoutableChannels() throws Exception {
        UUID userId = UUID.randomUUID();
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o");
        channel.setModelMapping(Map.of("gpt-5.3-codex", "upstream-codex"));

        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));
        when(metadataService.findByModelIds(anyCollection())).thenReturn(Map.of());

        mockMvc.perform(
                        get("/api/v1/models")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("gpt-4o"))
                .andExpect(jsonPath("$.data[1].id").value("gpt-5.3-codex"));
    }

    @Test
    void listKeepsWildcardModelContractsVisibleForRoutableChannels() throws Exception {
        UUID userId = UUID.randomUUID();
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o, gpt-5*");
        channel.setModelMapping(Map.of("claude-3*", "upstream-claude"));

        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));
        when(metadataService.findByModelIds(anyCollection())).thenReturn(Map.of());

        mockMvc.perform(
                        get("/api/v1/models")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("claude-3*"))
                .andExpect(jsonPath("$.data[1].id").value("gpt-4o"))
                .andExpect(jsonPath("$.data[2].id").value("gpt-5*"));

        verify(metadataService).findByModelIds(argThat(ids ->
                ids.size() == 3
                        && ids.contains("claude-3*")
                        && ids.contains("gpt-4o")
                        && ids.contains("gpt-5*")
        ));
    }

    @Test
    void listReturnsServerErrorWhenChannelCatalogFails() throws Exception {
        UUID userId = UUID.randomUUID();
        when(channelRouter.listRoutableChannels()).thenThrow(new IllegalStateException("redis down"));

        mockMvc.perform(
                        get("/api/v1/models")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SERVER_ERROR))
                .andExpect(jsonPath("$.message").value("model catalog unavailable"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void listFailsClosedWhenRolePolicyResolutionFails() throws Exception {
        UUID userId = UUID.randomUUID();
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o");

        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));
        when(rolePolicyService.resolveAllowedModels(eq(userId), isNull()))
                .thenThrow(new IllegalStateException("policy service down"));

        mockMvc.perform(
                        get("/api/v1/models")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SERVER_ERROR))
                .andExpect(jsonPath("$.message").value("model policy unavailable"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void modelsRouteRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }
}
