package com.webchat.adminapi.chat;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MultiChatAdminControllerTest {

    @Mock
    private MultiChatAdminService multiChatAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MultiChatAdminController controller = new MultiChatAdminController(multiChatAdminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void unauthenticatedMultiAgentDiscussionConfigRouteIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/multi-chat/multi-agent-discussion/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(multiChatAdminService);
    }

    @Test
    void configRouteReturnsBaseFeatureEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatAdminService.parallelConfig()).thenReturn(Map.of(
                "enabled", true,
                "maxModels", 3,
                "featureKey", "platform.multi-chat.enabled"
        ));

        mockMvc.perform(
                        get("/api/v1/admin/multi-chat/config")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxModels").value(3))
                .andExpect(jsonPath("$.data.featureKey").value("platform.multi-chat.enabled"));

        verify(multiChatAdminService).parallelConfig();
    }

    @Test
    void legacyRoleplayRoutesAreNotMapped() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roleplay/config"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/admin/roleplay/config").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void multiAgentDiscussionConfigUsesUnifiedResponseShape() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatAdminService.multiAgentDiscussionConfig()).thenReturn(Map.of(
                "enabled", true,
                "maxAgents", 5,
                "maxRounds", 18
        ));

        mockMvc.perform(
                        get("/api/v1/admin/multi-chat/multi-agent-discussion/config")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxAgents").value(5))
                .andExpect(jsonPath("$.data.maxRounds").value(18))
                .andExpect(jsonPath("$.data.maxRoles").doesNotExist());

        verify(multiChatAdminService).multiAgentDiscussionConfig();
    }

    @Test
    void updateMultiAgentDiscussionConfigPersistsUnifiedKeys() throws Exception {
        UUID userId = UUID.randomUUID();
        when(multiChatAdminService.updateMultiAgentDiscussionConfig(anyMap())).thenReturn(ApiResponse.ok(Map.of(
                "enabled", true,
                "maxAgents", 6,
                "maxRounds", 24
        )));

        mockMvc.perform(
                        put("/api/v1/admin/multi-chat/multi-agent-discussion/config")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "enabled": true,
                                          "maxAgents": 6,
                                          "maxRounds": 24
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.maxAgents").value(6))
                .andExpect(jsonPath("$.data.maxRounds").value(24));

        verify(multiChatAdminService).updateMultiAgentDiscussionConfig(anyMap());
    }
}
