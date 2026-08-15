package com.webchat.platformapi.ai.model;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelMetadataServiceTest {

    @Mock
    private AiModelMetadataRepository repository;

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private AiChannelRepository channelRepo;

    @Mock
    private AiModelCapabilityResolver capabilityResolver;

    private AiModelMetadataService service;

    @BeforeEach
    void setUp() {
        service = new AiModelMetadataService(repository, channelRouter, channelRepo, capabilityResolver);
    }

    @Test
    void listCatalogUsesOnlyRoutableChannelModels() {
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o, gpt-5.3-codex");
        channel.setModelMapping(Map.of("claude-3-7-sonnet", "claude-sonnet"));
        AiModelMetadataEntity priced = new AiModelMetadataEntity();
        priced.setModelId("gpt-4o");
        priced.setBillingEnabled(true);
        priced.setRequestPriceUsd(new BigDecimal("0.02"));
        priced.setPromptPriceUsd(new BigDecimal("0.01"));
        priced.setInputPriceUsdPer1M(new BigDecimal("5.50"));
        priced.setOutputPriceUsdPer1M(new BigDecimal("12.25"));

        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));
        when(repository.findAllByModelIdIn(anyCollection())).thenReturn(List.of(priced));
        when(capabilityResolver.resolve(any(), any()))
                .thenReturn(new AiModelCapabilityResolver.ImageCapability(false, "unknown"));

        Page<Map<String, Object>> page = service.listCatalog(PageRequest.of(0, 10));

        assertEquals(3, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(item -> "gpt-5.3-codex".equals(item.get("modelId"))));
        assertTrue(page.getContent().stream().anyMatch(item -> "claude-3-7-sonnet".equals(item.get("modelId"))));
        Map<String, Object> gpt = page.getContent().stream()
                .filter(item -> "gpt-4o".equals(item.get("modelId")))
                .findFirst()
                .orElseThrow();
        assertEquals(true, gpt.get("billingEnabled"));
        assertEquals(new BigDecimal("0.02"), gpt.get("requestPriceUsd"));
        assertEquals(new BigDecimal("0.01"), gpt.get("promptPriceUsd"));
        assertEquals(new BigDecimal("5.50"), gpt.get("inputPriceUsdPer1M"));
        assertEquals(new BigDecimal("12.25"), gpt.get("outputPriceUsdPer1M"));
        verify(channelRouter).listRoutableChannels();
    }

    @Test
    void upsertPersistsBillingFieldsAndClearsPricesWhenDisabled() {
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o");

        AiModelMetadataEntity existing = new AiModelMetadataEntity();
        existing.setModelId("gpt-4o");
        existing.setBillingEnabled(true);
        existing.setRequestPriceUsd(new BigDecimal("0.02"));
        existing.setPromptPriceUsd(new BigDecimal("0.01"));
        existing.setInputPriceUsdPer1M(new BigDecimal("5.50"));
        existing.setOutputPriceUsdPer1M(new BigDecimal("12.25"));

        ModelMetadataRequest enableRequest = new ModelMetadataRequest();
        enableRequest.setBillingEnabled(true);
        enableRequest.setRequestPriceUsd(new BigDecimal("0.03"));
        enableRequest.setPromptPriceUsd(new BigDecimal("0.02"));
        enableRequest.setInputPriceUsdPer1M(new BigDecimal("6.00"));
        enableRequest.setOutputPriceUsdPer1M(new BigDecimal("13.00"));

        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));
        when(repository.findByModelId("gpt-4o")).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(AiModelMetadataEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiModelMetadataEntity saved = service.upsert("gpt-4o", enableRequest);

        assertTrue(saved.isBillingEnabled());
        assertEquals(new BigDecimal("0.03"), saved.getRequestPriceUsd());
        assertEquals(new BigDecimal("0.02"), saved.getPromptPriceUsd());
        assertEquals(new BigDecimal("6.00"), saved.getInputPriceUsdPer1M());
        assertEquals(new BigDecimal("13.00"), saved.getOutputPriceUsdPer1M());

        ModelMetadataRequest disableRequest = new ModelMetadataRequest();
        disableRequest.setBillingEnabled(false);

        AiModelMetadataEntity disabled = service.upsert("gpt-4o", disableRequest);
        assertTrue(!disabled.isBillingEnabled());
        assertNull(disabled.getRequestPriceUsd());
        assertNull(disabled.getPromptPriceUsd());
        assertNull(disabled.getInputPriceUsdPer1M());
        assertNull(disabled.getOutputPriceUsdPer1M());

        verify(repository, atLeastOnce()).save(argThat(entity ->
                !entity.isBillingEnabled()
                        && entity.getRequestPriceUsd() == null
                        && entity.getPromptPriceUsd() == null
                        && entity.getInputPriceUsdPer1M() == null
                        && entity.getOutputPriceUsdPer1M() == null
        ));
    }
}
