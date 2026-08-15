package com.webchat.adminapi.ai.controller;

import com.webchat.adminapi.ai.service.AiChannelAdminService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
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
class AiChannelAdminControllerTest {

    @Mock
    private AiChannelAdminService aiChannelAdminService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        AiChannelAdminController controller = new AiChannelAdminController(aiChannelAdminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void listUsesAdminChannelRouteAndReturnsPagedPayload() throws Exception {
        when(aiChannelAdminService.list(0, 20, null)).thenReturn(ApiResponse.ok(Map.of(
                "items", List.of(
                        Map.of(
                                "id", 101L,
                                "name", "OpenAI Primary",
                                "keyCount", 1
                        )
                ),
                "total", 1,
                "page", 0,
                "size", 20
        )));

        mockMvc.perform(admin(get("/api/v1/admin/channels")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(101))
                .andExpect(jsonPath("$.data.items[0].name").value("OpenAI Primary"))
                .andExpect(jsonPath("$.data.items[0].keyCount").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(aiChannelAdminService).list(0, 20, null);
    }

    @Test
    void listPassesKeywordAndPagingToService() throws Exception {
        when(aiChannelAdminService.list(1, 2, "openai")).thenReturn(ApiResponse.ok(Map.of(
                "items", List.of(
                        Map.of(
                                "id", 101L,
                                "name", "OpenAI Primary",
                                "keyCount", 1
                        )
                ),
                "total", 3,
                "page", 1,
                "size", 2
        )));

        mockMvc.perform(admin(get("/api/v1/admin/channels").param("page", "1").param("size", "2").param("keyword", "openai")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].name").value("OpenAI Primary"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2));

        verify(aiChannelAdminService).list(1, 2, "openai");
    }

    @Test
    void listRejectsNonAdminRequests() throws Exception {
        mockMvc.perform(nonAdmin(get("/api/v1/admin/channels")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(aiChannelAdminService);
    }

    @Test
    void updateStatusReturnsNestedChannelPayload() throws Exception {
        when(aiChannelAdminService.updateStatus(eq(11L), any()))
                .thenReturn(ApiResponse.ok(Map.of(
                        "channel", Map.of(
                                "id", 11L,
                                "enabled", false,
                                "status", 2,
                                "keyCount", 1
                        )
                )));

        mockMvc.perform(
                        admin(put("/api/v1/admin/channels/{id}/status", 11L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":false,\"status\":2}"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.channel.id").value(11))
                .andExpect(jsonPath("$.data.channel.enabled").value(false))
                .andExpect(jsonPath("$.data.channel.status").value(2))
                .andExpect(jsonPath("$.data.channel.keyCount").value(1));

        verify(aiChannelAdminService).updateStatus(eq(11L), any());
    }

    @Test
    void updateStatusRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(
                        put("/api/v1/admin/channels/{id}/status", 11L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":false}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(aiChannelAdminService);
    }

    @Test
    void testAllUsesDedicatedRouteAndReturnsResultsEnvelope() throws Exception {
        when(aiChannelAdminService.testAll(eq(5), any()))
                .thenReturn(ApiResponse.ok(Map.of(
                        "total", 2,
                        "ok", 1,
                        "results", List.of(
                                Map.of("channelId", 11L, "statusCode", 200),
                                Map.of("channelId", 22L, "statusCode", 429)
                        )
                )));

        mockMvc.perform(
                        admin(post("/api/v1/admin/channels/test-all")
                                .param("limit", "5")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"model\":\"gpt-4o-mini\"}"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.ok").value(1))
                .andExpect(jsonPath("$.data.results[0].channelId").value(11))
                .andExpect(jsonPath("$.data.results[1].channelId").value(22))
                .andExpect(jsonPath("$.data.results[1].statusCode").value(429));

        verify(aiChannelAdminService).testAll(eq(5), any());
    }

    @Test
    void usageReturnsChannelArrayInsideSummaryEnvelope() throws Exception {
        when(aiChannelAdminService.usage(12))
                .thenReturn(ApiResponse.ok(Map.of(
                        "hours", 12,
                        "total", 9,
                        "done", 7,
                        "error", 2,
                        "channels", List.of(
                                Map.of(
                                        "id", 11L,
                                        "enabledKeys", 1,
                                        "disabledKeys", 1,
                                        "windowTotal", 9
                                ),
                                Map.of(
                                        "id", 22L,
                                        "windowTotal", 0
                                )
                        )
                )));

        mockMvc.perform(admin(get("/api/v1/admin/channels/usage").param("hours", "12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hours").value(12))
                .andExpect(jsonPath("$.data.total").value(9))
                .andExpect(jsonPath("$.data.done").value(7))
                .andExpect(jsonPath("$.data.error").value(2))
                .andExpect(jsonPath("$.data.channels[0].id").value(11))
                .andExpect(jsonPath("$.data.channels[0].enabledKeys").value(1))
                .andExpect(jsonPath("$.data.channels[0].disabledKeys").value(1))
                .andExpect(jsonPath("$.data.channels[0].windowTotal").value(9))
                .andExpect(jsonPath("$.data.channels[1].id").value(22))
                .andExpect(jsonPath("$.data.channels[1].windowTotal").value(0));

        verify(aiChannelAdminService).usage(12);
    }

    @Test
    void listKeysReturnsArrayPayloadForSelectedChannel() throws Exception {
        when(aiChannelAdminService.listKeys(11L)).thenReturn(ApiResponse.ok(List.of(
                Map.of(
                        "id", 201L,
                        "keyHash", "sha256:abcd",
                        "enabled", true,
                        "successCount", 3,
                        "lastSuccessAt", "2026-03-23T12:00:00Z"
                )
        )));

        mockMvc.perform(admin(get("/api/v1/admin/channels/{id}/keys", 11L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(201))
                .andExpect(jsonPath("$.data[0].keyHash").value("sha256:abcd"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].successCount").value(3))
                .andExpect(jsonPath("$.data[0].lastSuccessAt").value("2026-03-23T12:00:00Z"));

        verify(aiChannelAdminService).listKeys(11L);
    }

    @Test
    void fetchModelsAcceptsStringChannelId() throws Exception {
        when(aiChannelAdminService.fetchModels(any())).thenReturn(ApiResponse.ok(Map.of(
                "models", List.of("gpt-4o", "gpt-4o-mini"),
                "count", 2
        )));

        mockMvc.perform(
                        admin(post("/api/v1/admin/channels/fetch-models")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"channel_id\":\"11\"}"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.models[0]").value("gpt-4o"))
                .andExpect(jsonPath("$.data.models[1]").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.count").value(2));

        verify(aiChannelAdminService).fetchModels(any());
    }

    @Test
    void fetchModelsRejectsNonScalarChannelId() throws Exception {
        when(aiChannelAdminService.fetchModels(any()))
                .thenReturn(ApiResponse.error(ErrorCodes.PARAM_MISSING, "channel_id invalid"));

        mockMvc.perform(
                        admin(post("/api/v1/admin/channels/fetch-models")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"channel_id\":{\"id\":11}}"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("channel_id invalid"));

        verify(aiChannelAdminService).fetchModels(any());
    }

    @Test
    void deleteReturnsNullDataPayload() throws Exception {
        when(aiChannelAdminService.delete(11L)).thenReturn(ApiResponse.ok("deleted", null));

        mockMvc.perform(admin(delete("/api/v1/admin/channels/{id}", 11L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(aiChannelAdminService).delete(11L);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }

    private MockHttpServletRequestBuilder nonAdmin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user");
    }
}
