package com.webchat.platformapi.research;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResearchProjectControllerTest {

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
    private ResearchProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ResearchProperties();
        ResearchProjectController controller = new ResearchProjectController(projectService, exportService, runtimeConfigService, featureChecker);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        userId = UUID.randomUUID();
    }

    @Test
    void getConfigReturnsRuntimeFeatureStatusWithoutAuth() throws Exception {
        properties.setEnabled(false);
        properties.getLiterature().setEnabled(true);
        properties.getExperiment().setEnabled(false);
        properties.getPaper().setEnabled(false);
        when(runtimeConfigService.snapshot()).thenReturn(properties);

        mockMvc.perform(get("/api/v1/research/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.literatureEnabled").value(true))
                .andExpect(jsonPath("$.data.message").value("research assistant is disabled"));

        verify(runtimeConfigService).snapshot();
        verifyNoInteractions(featureChecker, projectService, exportService);
    }

    @Test
    void exportProjectReturnsLatestPaperPackage() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(exportService.exportProject(userId, projectId, "result")).thenReturn(Map.of(
                "filename", "paper.docx",
                "mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "contentBase64", "U29tZUJhc2U2NA=="
        ));

        mockMvc.perform(authenticated(get("/api/v1/research/projects/{id}/export", projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.filename").value("paper.docx"))
                .andExpect(jsonPath("$.data.mimeType").value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(jsonPath("$.data.contentBase64").value("U29tZUJhc2U2NA=="));

        verify(featureChecker).checkEnabled();
        verify(exportService).exportProject(userId, projectId, "result");
    }

    @Test
    void exportProjectRejectsAnonymousRequests() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/research/projects/{id}/export", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("user not authenticated"));

        verify(featureChecker).checkEnabled();
        verifyNoInteractions(exportService);
    }

    @Test
    void listProjectsReturnsApiResponseWhenResearchFeatureIsDisabled() throws Exception {
        ResearchProperties disabled = new ResearchProperties();
        disabled.setEnabled(false);
        when(runtimeConfigService.snapshot()).thenReturn(disabled);

        ResearchFeatureChecker realChecker = new ResearchFeatureChecker(runtimeConfigService);
        ResearchProjectController controller = new ResearchProjectController(
                projectService,
                exportService,
                runtimeConfigService,
                realChecker
        );
        MockMvc adviceMockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        adviceMockMvc.perform(authenticated(get("/api/v1/research/projects")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("Research Assistant is not enabled"));

        verify(runtimeConfigService).snapshot();
        verifyNoInteractions(projectService);
    }

    @Test
    void startRunForwardsResumePayload() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID sourceRunId = UUID.randomUUID();
        when(projectService.startRun(eq(userId), eq(projectId), eq(Map.of(
                "sourceRunId", sourceRunId.toString(),
                "restartFromStage", 17
        )))).thenReturn(Map.of(
                "id", UUID.randomUUID().toString(),
                "status", "running",
                "currentStage", 17
        ));

        mockMvc.perform(authenticated(
                        post("/api/v1/research/projects/{id}/runs", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"sourceRunId":"%s","restartFromStage":17}
                                        """.formatted(sourceRunId))
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.currentStage").value(17));

        verify(featureChecker).checkEnabled();
        verify(projectService).startRun(eq(userId), eq(projectId), eq(Map.of(
                "sourceRunId", sourceRunId.toString(),
                "restartFromStage", 17
        )));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(JwtAuthFilter.ATTR_USER_ID, userId);
    }
}
