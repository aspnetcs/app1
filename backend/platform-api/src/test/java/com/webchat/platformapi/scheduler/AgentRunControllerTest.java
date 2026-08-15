package com.webchat.platformapi.scheduler;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentRunControllerTest {

    @Mock
    private AgentRunService runService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentRunController(runService)).build();
    }

    @Test
    void createRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/agent-runs").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void createCallsServiceWithParsedPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(runService.createRun(eq(userId), eq(agentId), eq(7L)))
                .thenReturn(Map.of("id", UUID.randomUUID().toString(), "status", "pending"));

        mockMvc.perform(
                        post("/api/v1/agent-runs")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"agentId\":\"" + agentId + "\",\"requestedChannelId\":7}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("pending"));

        verify(runService).createRun(userId, agentId, 7L);
    }

    @Test
    void startMapsIllegalStateToParamMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(runService.startRun(eq(userId), eq(runId))).thenThrow(new IllegalStateException("run is not approved"));

        mockMvc.perform(
                        post("/api/v1/agent-runs/{id}/start", runId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("run is not approved"));
    }
}

