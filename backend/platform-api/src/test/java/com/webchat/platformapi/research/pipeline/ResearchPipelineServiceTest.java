package com.webchat.platformapi.research.pipeline;

import com.webchat.platformapi.research.ResearchRuntimeConfigService;
import com.webchat.platformapi.research.ResearchStage;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchPipelineServiceTest {

    @Mock
    private ResearchStageExecutor stageExecutor;

    @Mock
    private ResearchRunRepository runRepo;

    @Mock
    private ResearchProjectRepository projectRepo;

    @Mock
    private ResearchStageLogRepository stageLogRepo;

    @Mock
    private ResearchRuntimeConfigService runtimeConfigService;

    private ResearchPipelineService service;

    @BeforeEach
    void setUp() {
        service = spy(new ResearchPipelineService(
                stageExecutor,
                runRepo,
                projectRepo,
                stageLogRepo,
                runtimeConfigService
        ));
    }

    @Test
    void handleFailureQueuesRollbackWithoutStartingAnotherPipelineInline() {
        UUID runId = UUID.randomUUID();
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(UUID.randomUUID());
        run.setStatus("running");
        run.setCurrentStage(ResearchStage.RESEARCH_DECISION.getNumber());
        run.setIteration(1);

        when(runRepo.findById(runId)).thenReturn(Optional.of(run));

        service.handleFailure(runId, ResearchStage.RESEARCH_DECISION, StageResult.failure("boom"));

        assertEquals(ResearchStage.HYPOTHESIS_GEN.getNumber(), run.getCurrentStage());
        assertEquals("running", run.getStatus());
        assertEquals(2, run.getIteration());
        verify(runRepo).save(run);
        verify(service, never()).startPipeline(any(UUID.class));
    }

    @Test
    void handleFailureMarksRunFailedWhenRollbackIsNotAvailable() {
        UUID runId = UUID.randomUUID();
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(UUID.randomUUID());
        run.setStatus("running");
        run.setCurrentStage(ResearchStage.TOPIC_INIT.getNumber());
        run.setIteration(3);

        when(runRepo.findById(runId)).thenReturn(Optional.of(run));

        service.handleFailure(runId, ResearchStage.TOPIC_INIT, StageResult.failure("boom"));

        assertEquals("failed", run.getStatus());
        assertNotNull(run.getCompletedAt());
        verify(runRepo).save(run);
        verify(service, never()).startPipeline(any(UUID.class));
    }

    @Test
    void restoreStageOutputContentExtractsPersistedMarkdownContent() {
        String restored = service.restoreStageOutputContent("{\"format\":\"markdown\",\"content\":\"# Final Draft\\nBody\"}");

        assertEquals("# Final Draft\nBody", restored);
    }

    @Test
    void restoreStageOutputContentFallsBackToRawPayloadWhenLegacyContentIsPlainText() {
        String restored = service.restoreStageOutputContent("legacy plain text");

        assertEquals("legacy plain text", restored);
    }

    @Test
    void restoreStageOutputContentReturnsNullForBlankContentField() {
        String restored = service.restoreStageOutputContent("{\"format\":\"markdown\",\"content\":\"   \"}");

        assertNull(restored);
    }

    @Test
    void shouldPauseAfterStageReturnsFalseForAutoGateStages() {
        assertEquals(false, service.shouldPauseAfterStage(
                ResearchStage.LITERATURE_SCREEN,
                "auto",
                java.util.Set.of(5)
        ));
    }

    @Test
    void shouldPauseAfterStageReturnsTrueForManualGateStages() {
        assertEquals(true, service.shouldPauseAfterStage(
                ResearchStage.LITERATURE_SCREEN,
                "manual",
                java.util.Set.of()
        ));
    }

    @Test
    void shouldPauseAfterStageReturnsTrueForManualCustomPauseStages() {
        assertEquals(true, service.shouldPauseAfterStage(
                ResearchStage.CODE_GENERATION,
                "manual",
                java.util.Set.of(ResearchStage.CODE_GENERATION.getNumber())
        ));
    }

    @Test
    void resumeOrphanedRunsOnStartupReservesAndRestartsRunningRuns() {
        UUID runId = UUID.randomUUID();
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(UUID.randomUUID());
        run.setStatus("running");

        when(runRepo.findByStatusOrderByStartedAtAsc("running")).thenReturn(List.of(run));
        doReturn(true).when(service).tryReservePipeline(runId);
        doNothing().when(service).activateReservedPipeline(runId);

        service.resumeOrphanedRunsOnStartup();

        verify(service).tryReservePipeline(runId);
        verify(service).activateReservedPipeline(runId);
    }

    @Test
    void handleUnhandledPipelineThrowableMarksRunFailed() {
        UUID runId = UUID.randomUUID();
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(UUID.randomUUID());
        run.setStatus("running");

        when(runRepo.findById(runId)).thenReturn(Optional.of(run));

        service.handleUnhandledPipelineThrowable(runId, new AssertionError("pipeline thread died"));

        assertEquals("failed", run.getStatus());
        assertNotNull(run.getCompletedAt());
        verify(runRepo).save(run);
    }
}
