package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.model.AiModelCapabilityResolver;
import com.webchat.platformapi.ai.model.AiModelMetadataEntity;
import com.webchat.platformapi.ai.model.AiModelMetadataService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ModelAdminControllerTest {

    @Mock
    private AiModelMetadataService metadataService;

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private ChannelMonitor channelMonitor;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelAdminController(metadataService, channelRouter, channelMonitor, new AiModelCapabilityResolver())
        ).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void listRejectsNonAdminRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(metadataService);
    }

    @Test
    void listReturnsPagedEnvelope() throws Exception {
        when(metadataService.listCatalog(PageRequest.of(1, 2), null)).thenReturn(
                new PageImpl<>(List.of(Map.of("modelId", "gpt-4o", "name", "GPT-4o")), PageRequest.of(1, 2), 11)
        );

        mockMvc.perform(admin(get("/api/v1/admin/models").param("page", "1").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].modelId").value("gpt-4o"))
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2));

        verify(metadataService).listCatalog(PageRequest.of(1, 2), null);
    }

    @Test
    void listPassesKeywordToService() throws Exception {
        when(metadataService.listCatalog(PageRequest.of(0, 25), "gpt")).thenReturn(
                new PageImpl<>(List.of(Map.of("modelId", "gpt-4o", "name", "GPT-4o")), PageRequest.of(0, 25), 1)
        );

        mockMvc.perform(admin(get("/api/v1/admin/models").param("keyword", "gpt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].modelId").value("gpt-4o"));

        verify(metadataService).listCatalog(PageRequest.of(0, 25), "gpt");
    }

    @Test
    void listReturnsFullCatalogWhenSizeIsZero() throws Exception {
        when(metadataService.listCatalogAll("gpt")).thenReturn(List.of(
                Map.of("modelId", "gpt-4o", "name", "GPT-4o"),
                Map.of("modelId", "gpt-4o-mini", "name", "GPT-4o Mini")
        ));

        mockMvc.perform(admin(get("/api/v1/admin/models").param("size", "0").param("keyword", "gpt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.items[1].modelId").value("gpt-4o-mini"));

        verify(metadataService).listCatalogAll("gpt");
    }

    @Test
    void updateUsesPathVariableAndBody() throws Exception {
        AiModelMetadataEntity entity = new AiModelMetadataEntity();
        entity.setModelId("gpt-4o");
        entity.setName("GPT-4o");
        entity.setAvatar("bot");
        entity.setDescription("flagship");
        entity.setPinned(true);
        entity.setSortOrder(3);
        entity.setDefaultSelected(true);
        entity.setMultiChatEnabled(true);
        entity.setBillingEnabled(true);
        entity.setRequestPriceUsd(new BigDecimal("0.03"));
        entity.setPromptPriceUsd(new BigDecimal("0.02"));
        entity.setInputPriceUsdPer1M(new BigDecimal("6.00"));
        entity.setOutputPriceUsdPer1M(new BigDecimal("13.00"));
        entity.setImageParsingOverride(false);
        entity.setCreatedAt(Instant.parse("2026-03-24T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-24T11:00:00Z"));
        when(metadataService.upsert(eq("gpt-4o"), any())).thenReturn(entity);

        mockMvc.perform(
                        admin(put("/api/v1/admin/models/{modelId}", "gpt-4o")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "GPT-4o",
                                          "pinned": true,
                                          "sortOrder": 3,
                                          "defaultSelected": true,
                                          "multiChatEnabled": true
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelId").value("gpt-4o"))
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.sortOrder").value(3))
                .andExpect(jsonPath("$.data.defaultSelected").value(true))
                .andExpect(jsonPath("$.data.billingEnabled").value(true))
                .andExpect(jsonPath("$.data.requestPriceUsd").value(0.03))
                .andExpect(jsonPath("$.data.promptPriceUsd").value(0.02))
                .andExpect(jsonPath("$.data.inputPriceUsdPer1M").value(6.00))
                .andExpect(jsonPath("$.data.outputPriceUsdPer1M").value(13.00))
                .andExpect(jsonPath("$.data.supportsImageParsing").value(false))
                .andExpect(jsonPath("$.data.supportsImageParsingSource").value("manual"))
                .andExpect(jsonPath("$.data.imageParsingOverride").value(false));
    }

    @Test
    void reorderBindsModelIdListPayload() throws Exception {
        mockMvc.perform(
                        admin(post("/api/v1/admin/models/reorder")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "modelIds": ["gpt-4o", "gpt-4o-mini"]
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(metadataService).reorder(captor.capture());
        assertEquals(List.of("gpt-4o", "gpt-4o-mini"), captor.getValue());
    }

    @Test
    void deleteUsesModelIdPathVariable() throws Exception {
        mockMvc.perform(admin(delete("/api/v1/admin/models/{modelId}", "gpt-4o")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(metadataService).deleteByModelId("gpt-4o");
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
