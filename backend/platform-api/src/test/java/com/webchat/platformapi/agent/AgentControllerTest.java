package com.webchat.platformapi.agent;

import com.webchat.platformapi.auth.group.UserGroupService;
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

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentService agentService;

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentController controller = new AgentController(agentService, userGroupService, false);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void marketRoutePassesCategoryFilterThroughToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(agentService.listMarket("prompt")).thenReturn(List.of(Map.of(
                "id", UUID.randomUUID().toString(),
                "name", "Prompt Coach",
                "category", "prompt"
        )));

        mockMvc.perform(
                        get("/api/v1/agents")
                                .param("category", "prompt")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("Prompt Coach"))
                .andExpect(jsonPath("$.data[0].category").value("prompt"));

        verify(agentService).listMarket("prompt");
    }

    @Test
    void marketRouteKeepsUnfilteredBehaviorWhenCategoryIsAbsent() throws Exception {
        UUID userId = UUID.randomUUID();
        when(agentService.listMarket(null)).thenReturn(List.of(Map.of(
                "id", UUID.randomUUID().toString(),
                "name", "Template Builder",
                "category", "template"
        )));

        mockMvc.perform(
                        get("/api/v1/agents")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("Template Builder"));

        verify(agentService).listMarket(isNull());
    }

    @Test
    void marketRouteRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void getAgentRouteReturnsOwnedUserAgentDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(agentService.getAgent(userId, agentId)).thenReturn(Map.of(
                "id", agentId.toString(),
                "name", "Private Coach",
                "scope", "USER"
        ));

        mockMvc.perform(
                        get("/api/v1/agents/{id}", agentId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Private Coach"))
                .andExpect(jsonPath("$.data.scope").value("USER"));

        verify(agentService).getAgent(userId, agentId);
    }

    @Test
    void getAgentRouteReturnsSystemAgentDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(agentService.getAgent(userId, agentId)).thenReturn(Map.of(
                "id", agentId.toString(),
                "name", "Prompt Coach",
                "scope", "SYSTEM"
        ));

        mockMvc.perform(
                        get("/api/v1/agents/{id}", agentId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Prompt Coach"))
                .andExpect(jsonPath("$.data.scope").value("SYSTEM"));

        verify(agentService).getAgent(userId, agentId);
    }

    @Test
    void getAgentRouteReturnsNotFoundWhenServiceRejectsNonOwnedUserAgent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(agentService.getAgent(userId, agentId)).thenThrow(new IllegalArgumentException("agent not found"));

        mockMvc.perform(
                        get("/api/v1/agents/{id}", agentId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("agent not found"));

        verify(agentService).getAgent(userId, agentId);
    }

    @Test
    void installAgentRouteReturnsNotFoundWhenServiceRejectsUserFacingInstall() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(agentService.installAgent(userId, agentId)).thenThrow(new IllegalArgumentException("agent not found"));

        mockMvc.perform(
                        post("/api/v1/agents/{id}/install", agentId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("agent not found"));

        verify(agentService).installAgent(userId, agentId);
    }
}
