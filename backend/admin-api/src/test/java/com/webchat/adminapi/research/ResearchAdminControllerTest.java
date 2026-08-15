package com.webchat.adminapi.research;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.config.SysConfigService;
import com.webchat.platformapi.research.ResearchProperties;
import com.webchat.platformapi.research.ResearchRuntimeConfigService;
import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResearchAdminControllerTest {

    @Mock
    private ResearchProjectRepository projectRepo;

    @Mock
    private ResearchRunRepository runRepo;

    @Mock
    private ResearchStageLogRepository stageLogRepo;

    @Mock
    private SysConfigService sysConfigService;

    private ResearchProperties properties;
    private ResearchRuntimeConfigService runtimeConfigService;
    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        properties = new ResearchProperties();
        properties.setEnabled(true);
        properties.setMaxConcurrentPipelines(4);
        properties.setStageTimeoutMinutes(45);
        properties.getLlm().setModel("gpt-4o");
        properties.getLlm().setMaxTokens(4096);
        properties.getLlm().setTemperature(0.3);
        properties.getLiterature().setEnabled(true);
        properties.getLiterature().setSources("openalex,semantic_scholar");
        properties.getLiterature().setMaxResultsPerSource(12);
        properties.getExperiment().setEnabled(true);
        properties.getExperiment().setMode("dry-run");
        properties.getExperiment().setTimeBudgetSec(180);
        properties.getPaper().setEnabled(true);
        properties.getPaper().setMaxIterations(3);
        properties.getPaper().setQualityThreshold(8.5);

        runtimeConfigService = new ResearchRuntimeConfigService(properties, objectProvider(sysConfigService));

        ResearchAdminController controller = new ResearchAdminController(
                runtimeConfigService,
                projectRepo,
                runRepo,
                stageLogRepo
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void getConfigReturnsStructuredResearchConfig() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/research/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxConcurrentPipelines").value(4))
                .andExpect(jsonPath("$.data.llm.model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.literature.sources").value("openalex,semantic_scholar"))
                .andExpect(jsonPath("$.data.paper.qualityThreshold").value(8.5));
    }

    @Test
    void updateConfigPersistsOverridesAndReturnsUpdatedPayload() throws Exception {
        mockMvc.perform(
                        admin(put("/api/v1/admin/research/config")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "enabled": false,
                                          "maxConcurrentPipelines": 6,
                                          "llm": { "model": "gpt-4.1", "maxTokens": 8192, "temperature": 0.6 },
                                          "literature": { "sources": ["openalex", "arxiv"], "maxResultsPerSource": 20 },
                                          "paper": { "maxIterations": 5, "qualityThreshold": 9.1 }
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.maxConcurrentPipelines").value(6))
                .andExpect(jsonPath("$.data.llm.model").value("gpt-4.1"))
                .andExpect(jsonPath("$.data.literature.sources").value("openalex,arxiv"))
                .andExpect(jsonPath("$.data.paper.maxIterations").value(5))
                .andExpect(jsonPath("$.data.paper.qualityThreshold").value(9.1));

        verify(sysConfigService).set("platform.research-assistant.enabled", "false");
        verify(sysConfigService).set("research.enabled", "false");
        verify(sysConfigService).set("platform.research-assistant.max-concurrent-pipelines", "6");
        verify(sysConfigService).set("research.max_concurrent_pipelines", "6");
        verify(sysConfigService).set("platform.research-assistant.llm.model", "gpt-4.1");
        verify(sysConfigService).set("research.llm.model", "gpt-4.1");
        verify(sysConfigService).set("platform.research-assistant.paper.max-iterations", "5");
        verify(sysConfigService).set("research.paper.max_iterations", "5");
    }

    @Test
    void getStatsIncludesActiveRunsAndAverageDuration() throws Exception {
        ResearchProjectEntity activeProject = new ResearchProjectEntity();
        activeProject.setId(UUID.randomUUID());
        activeProject.setUserId(UUID.randomUUID());
        activeProject.setStatus("active");

        ResearchProjectEntity archivedProject = new ResearchProjectEntity();
        archivedProject.setId(UUID.randomUUID());
        archivedProject.setUserId(UUID.randomUUID());
        archivedProject.setStatus("archived");

        ResearchRunEntity runningRun = new ResearchRunEntity();
        runningRun.setId(UUID.randomUUID());
        runningRun.setProjectId(activeProject.getId());
        runningRun.setStatus("running");

        ResearchRunEntity completedRun = new ResearchRunEntity();
        completedRun.setId(UUID.randomUUID());
        completedRun.setProjectId(activeProject.getId());
        completedRun.setStatus("completed");
        completedRun.setStartedAt(Instant.parse("2026-03-25T10:00:00Z"));
        completedRun.setCompletedAt(Instant.parse("2026-03-25T10:02:00Z"));

        when(projectRepo.count()).thenReturn(2L);
        when(projectRepo.findAll()).thenReturn(List.of(activeProject, archivedProject));
        when(runRepo.findAll()).thenReturn(List.of(runningRun, completedRun));

        mockMvc.perform(admin(get("/api/v1/admin/research/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalProjects").value(2))
                .andExpect(jsonPath("$.data.activeProjects").value(1))
                .andExpect(jsonPath("$.data.totalRuns").value(2))
                .andExpect(jsonPath("$.data.activeRuns").value(1))
                .andExpect(jsonPath("$.data.completedRuns").value(1))
                .andExpect(jsonPath("$.data.avgCompletionSeconds").value(120.0));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }

    private static ObjectProvider<SysConfigService> objectProvider(SysConfigService sysConfigService) {
        return new ObjectProvider<>() {
            @Override
            public SysConfigService getObject(Object... args) {
                return sysConfigService;
            }

            @Override
            public SysConfigService getIfAvailable() {
                return sysConfigService;
            }

            @Override
            public SysConfigService getIfUnique() {
                return sysConfigService;
            }

            @Override
            public SysConfigService getObject() {
                return sysConfigService;
            }

            @Override
            public Iterator<SysConfigService> iterator() {
                return List.of(sysConfigService).iterator();
            }
        };
    }
}
