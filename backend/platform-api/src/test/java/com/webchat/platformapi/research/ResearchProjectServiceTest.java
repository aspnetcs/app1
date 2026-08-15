package com.webchat.platformapi.research;

import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.pipeline.ResearchPipelineService;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchProjectServiceTest {

    @Mock
    private ResearchProjectRepository projectRepo;

    @Mock
    private ResearchRunRepository runRepo;

    @Mock
    private ResearchStageLogRepository stageLogRepo;

    @Mock
    private ResearchPipelineService pipelineService;

    private ResearchProjectService service;

    @BeforeEach
    void setUp() {
        service = new ResearchProjectService(
                projectRepo,
                runRepo,
                stageLogRepo,
                new ResearchProperties(),
                pipelineService
        );
    }

    @Test
    void startRunRejectsWhenPipelineSlotCannotBeReserved() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ResearchProjectEntity project = project(userId, projectId);

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdAndStatusOrderByRunNumberDesc(projectId, "running"))
                .thenReturn(Optional.empty());
        when(runRepo.countByProjectId(projectId)).thenReturn(0);
        when(pipelineService.tryReservePipeline(any(UUID.class))).thenReturn(false);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.startRun(userId, projectId)
        );

        assertEquals("research pipeline is busy, please retry later", error.getMessage());
        verify(runRepo, never()).save(any(ResearchRunEntity.class));
        verify(projectRepo, never()).save(any(ResearchProjectEntity.class));
        verify(pipelineService, never()).activateReservedPipeline(any(UUID.class));
    }

    @Test
    void startRunActivatesReservedPipelineImmediatelyWhenNoTransactionSynchronizationExists() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ResearchProjectEntity project = project(userId, projectId);

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdAndStatusOrderByRunNumberDesc(projectId, "running"))
                .thenReturn(Optional.empty());
        when(runRepo.countByProjectId(projectId)).thenReturn(0);
        when(pipelineService.tryReservePipeline(any(UUID.class))).thenReturn(true);

        Map<String, Object> result = service.startRun(userId, projectId);

        verify(runRepo).save(any(ResearchRunEntity.class));
        verify(projectRepo).save(project);
        verify(pipelineService).activateReservedPipeline(UUID.fromString((String) result.get("id")));
        assertEquals("running", result.get("status"));
    }

    @Test
    void approveGateRejectsWhenPipelineSlotCannotBeReserved() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ResearchProjectEntity project = project(userId, projectId);
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(projectId);
        run.setStatus("waiting_approval");
        run.setCurrentStage(5);

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(pipelineService.tryReservePipeline(runId)).thenReturn(false);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.approveGate(userId, projectId, runId, Map.of("decision", "approve"))
        );

        assertEquals("research pipeline is busy, please retry later", error.getMessage());
        assertEquals("waiting_approval", run.getStatus());
        assertEquals(5, run.getCurrentStage());
        verify(runRepo, never()).save(run);
        verify(pipelineService, never()).activateReservedPipeline(runId);
    }

    @Test
    void approveGateMarksCurrentStageLogCompletedBeforeResuming() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ResearchProjectEntity project = project(userId, projectId);
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(projectId);
        run.setStatus("waiting_approval");
        run.setCurrentStage(5);

        ResearchStageLogEntity stageLog = new ResearchStageLogEntity();
        stageLog.setRunId(runId);
        stageLog.setStageNumber(5);
        stageLog.setStatus("waiting_approval");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(stageLogRepo.findFirstByRunIdAndStageNumberOrderByCreatedAtDesc(runId, 5)).thenReturn(Optional.of(stageLog));
        when(pipelineService.tryReservePipeline(runId)).thenReturn(true);

        Map<String, Object> result = service.approveGate(userId, projectId, runId, Map.of("decision", "approve"));

        assertEquals("running", result.get("status"));
        assertEquals(6, result.get("currentStage"));
        assertEquals("completed", stageLog.getStatus());
        assertEquals("approve", stageLog.getDecision());
        verify(stageLogRepo).save(stageLog);
        verify(runRepo).save(run);
        verify(pipelineService).activateReservedPipeline(runId);
    }

    @Test
    void getProjectReturnsLatestRunInsteadOfRunsHistory() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID latestRunId = UUID.randomUUID();
        ResearchProjectEntity project = project(userId, projectId);

        ResearchRunEntity latestRun = new ResearchRunEntity();
        latestRun.setId(latestRunId);
        latestRun.setProjectId(projectId);
        latestRun.setRunNumber(3);
        latestRun.setCurrentStage(6);
        latestRun.setStatus("running");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)).thenReturn(Optional.of(latestRun));

        Map<String, Object> result = service.getProject(userId, projectId);

        assertFalse(result.containsKey("runs"));
        assertEquals("running", ((Map<?, ?>) result.get("latestRun")).get("status"));
        assertEquals("3", String.valueOf(((Map<?, ?>) result.get("latestRun")).get("runNumber")));
    }

    @Test
    void getRunStatusBackfillsLegacyStructuredStageOutput() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ResearchProjectEntity project = project(userId, projectId);

        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(projectId);
        run.setRunNumber(1);
        run.setCurrentStage(22);
        run.setStatus("completed");

        ResearchStageLogEntity stageLog = new ResearchStageLogEntity();
        stageLog.setId(UUID.randomUUID());
        stageLog.setRunId(runId);
        stageLog.setStageNumber(22);
        stageLog.setStageName("export_publish");
        stageLog.setStatus("completed");
        stageLog.setOutputJson("{\"format\":\"markdown\",\"document\":\"# Final Paper\"}");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId)).thenReturn(List.of(stageLog));

        Map<String, Object> result = service.getRunStatus(userId, projectId, runId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stageLogs = (List<Map<String, Object>>) result.get("stageLogs");
        assertEquals(1, stageLogs.size());
        assertTrue(String.valueOf(stageLogs.get(0).get("outputJson")).contains("\"content\":\"# Final Paper\""));
        verify(stageLogRepo).save(argThat(log ->
                log.getId().equals(stageLog.getId())
                        && log.getOutputJson() != null
                        && log.getOutputJson().contains("\"content\":\"# Final Paper\"")
        ));
    }

    @Test
    void listProjectsIncludesArchivedProjects() {
        UUID userId = UUID.randomUUID();
        ResearchProjectEntity activeProject = project(userId, UUID.randomUUID());
        activeProject.setStatus("active");
        ResearchProjectEntity archivedProject = project(userId, UUID.randomUUID());
        archivedProject.setStatus("archived");

        when(projectRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(activeProject, archivedProject));

        List<Map<String, Object>> result = service.listProjects(userId);

        assertEquals(2, result.size());
        assertEquals("active", result.get(0).get("status"));
        assertEquals("archived", result.get(1).get("status"));
    }

    @Test
    void startRunFromFailedStageCopiesCompletedContextBeforeRestartStage() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sourceRunId = UUID.randomUUID();
        ResearchProjectEntity project = project(userId, projectId);

        ResearchRunEntity sourceRun = new ResearchRunEntity();
        sourceRun.setId(sourceRunId);
        sourceRun.setProjectId(projectId);
        sourceRun.setRunNumber(2);
        sourceRun.setCurrentStage(17);
        sourceRun.setStatus("failed");

        ResearchStageLogEntity stage16 = new ResearchStageLogEntity();
        stage16.setRunId(sourceRunId);
        stage16.setStageNumber(16);
        stage16.setStageName("paper_outline");
        stage16.setStatus("completed");
        stage16.setOutputJson("{\"format\":\"markdown\",\"content\":\"outline\"}");

        ResearchStageLogEntity stage17 = new ResearchStageLogEntity();
        stage17.setRunId(sourceRunId);
        stage17.setStageNumber(17);
        stage17.setStageName("paper_draft");
        stage17.setStatus("failed");
        stage17.setErrorMessage("boom");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdAndStatusOrderByRunNumberDesc(projectId, "running"))
                .thenReturn(Optional.empty());
        when(runRepo.countByProjectId(projectId)).thenReturn(2);
        when(runRepo.findById(sourceRunId)).thenReturn(Optional.of(sourceRun));
        when(stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(sourceRunId)).thenReturn(java.util.List.of(stage16, stage17));
        when(pipelineService.tryReservePipeline(any(UUID.class))).thenReturn(true);

        Map<String, Object> result = service.startRun(userId, projectId, Map.of(
                "sourceRunId", sourceRunId.toString(),
                "restartFromStage", 17
        ));

        verify(stageLogRepo).save(argThat(log ->
                log.getRunId().equals(UUID.fromString((String) result.get("id")))
                        && log.getStageNumber() == 16
                        && "completed".equals(log.getStatus())
                        && "{\"format\":\"markdown\",\"content\":\"outline\"}".equals(log.getOutputJson())
        ));
    }

    private static ResearchProjectEntity project(UUID userId, UUID projectId) {
        ResearchProjectEntity project = new ResearchProjectEntity();
        project.setId(projectId);
        project.setUserId(userId);
        project.setName("Project");
        project.setTopic("Topic");
        project.setStatus("draft");
        return project;
    }
}
