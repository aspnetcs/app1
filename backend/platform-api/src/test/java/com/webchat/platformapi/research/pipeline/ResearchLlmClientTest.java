package com.webchat.platformapi.research.pipeline;

import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.research.ResearchProperties;
import com.webchat.platformapi.research.ResearchRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchLlmClientTest {

    @Mock
    private AdapterFactory adapterFactory;

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private AiCryptoService cryptoService;

    @Mock
    private SsrfGuard ssrfGuard;

    @Mock
    private ResearchRuntimeConfigService runtimeConfigService;

    @Test
    void resolveModelUsesExplicitResearchModelWhenConfigured() {
        ResearchProperties properties = new ResearchProperties();
        properties.getLlm().setModel("gpt-5");

        ResearchLlmClient client = new ResearchLlmClient(
                adapterFactory, channelRouter, cryptoService, ssrfGuard, runtimeConfigService
        );

        assertEquals("gpt-5", client.resolveModel(properties));
    }

    @Test
    void resolveModelFallsBackToFirstRoutableChannelModel() {
        ResearchProperties properties = new ResearchProperties();
        properties.getLlm().setModel("");

        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-5,gpt-5-codex");
        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));

        ResearchLlmClient client = new ResearchLlmClient(
                adapterFactory, channelRouter, cryptoService, ssrfGuard, runtimeConfigService
        );

        assertEquals("gpt-5", client.resolveModel(properties));
    }
}
