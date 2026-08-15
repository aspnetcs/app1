package com.webchat.platformapi.market;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarketControllerTest {

    @Mock
    private PlatformMarketService marketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketController(marketService)).build();
    }

    @Test
    void listAssetsRejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/v1/market/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void listSavedAssetsReturnsServicePayload() throws Exception {
        UUID userId = UUID.randomUUID();
        when(marketService.listSavedAssets(eq(userId), eq("MCP"))).thenReturn(List.of(
                Map.of("assetType", "MCP", "sourceId", "12", "saved", true)
        ));

        mockMvc.perform(get("/api/v1/market/saved-assets")
                        .param("assetType", "MCP")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].assetType").value("MCP"))
                .andExpect(jsonPath("$.data[0].saved").value(true));
    }

    @Test
    void saveAssetPassesPathVariablesToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(marketService.saveAsset(eq(userId), eq(MarketAssetType.AGENT), eq("abc"))).thenReturn(Map.of(
                "assetType", "AGENT",
                "sourceId", "abc",
                "saved", true
        ));

        mockMvc.perform(post("/api/v1/market/assets/{assetType}/{sourceId}/save", "AGENT", "abc")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.saved").value(true))
                .andExpect(jsonPath("$.data.sourceId").value("abc"));
    }

    @Test
    void deleteSavedAssetCallsService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/market/assets/{assetType}/{sourceId}/save", "KNOWLEDGE", "kb-1")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(marketService).unsaveAsset(userId, MarketAssetType.KNOWLEDGE, "kb-1");
    }
}
