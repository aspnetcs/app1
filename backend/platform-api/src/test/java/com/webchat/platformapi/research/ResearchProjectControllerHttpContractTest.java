package com.webchat.platformapi.research;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResearchProjectControllerHttpContractTest {

    @Mock
    private ResearchProjectService projectService;
    @Mock
    private ResearchExportService exportService;
    @Mock
    private ResearchRuntimeConfigService runtimeConfigService;
    @Mock
    private ResearchFeatureChecker featureChecker;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ResearchProjectController(
                projectService,
                exportService,
                runtimeConfigService,
                featureChecker
        )).build();
        userId = UUID.fromString("00000000-0000-0000-0000-000000000055");
    }

    @Test
    void createProjectRouteRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(
                        post("/api/v1/research/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "topic": "AI"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("user not authenticated"));

        verify(featureChecker).checkEnabled();
    }

    @Test
    void createProjectRouteBindsRequestBodyAndDelegates() throws Exception {
        when(projectService.createProject(eq(userId), anyMap())).thenReturn(Map.of("id", "project-1"));

        mockMvc.perform(
                        post("/api/v1/research/projects")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "topic": "AI safety",
                                          "goal": "write a paper"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("project-1"));

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(projectService).createProject(eq(userId), bodyCaptor.capture());
        assertEquals("AI safety", bodyCaptor.getValue().get("topic"));
        assertEquals("write a paper", bodyCaptor.getValue().get("goal"));
    }

    @Test
    void deleteProjectRouteBindsPathVariableAndDelegates() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000066");

        mockMvc.perform(
                        delete("/api/v1/research/projects/{id}", projectId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"));

        verify(projectService).archiveProject(userId, projectId);
    }

    @Test
    void approveGateRouteBindsBodyAndDelegates() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000077");
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000088");
        when(projectService.approveGate(eq(userId), eq(projectId), eq(runId), anyMap()))
                .thenReturn(Map.of("status", "approved"));

        mockMvc.perform(
                        post("/api/v1/research/projects/{projectId}/runs/{runId}/approve", projectId, runId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "decision": "approve"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("approved"));

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(projectService).approveGate(eq(userId), eq(projectId), eq(runId), bodyCaptor.capture());
        assertEquals("approve", bodyCaptor.getValue().get("decision"));
    }
}
