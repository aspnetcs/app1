package com.webchat.platformapi.research.pipeline;

import com.webchat.platformapi.research.ResearchStage;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.literature.LiteratureResult;
import com.webchat.platformapi.research.literature.LiteratureSearchService;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchStageExecutorTest {

    @Mock
    private ResearchLlmClient llmClient;

    @Mock
    private LiteratureSearchService literatureService;

    @Mock
    private ResearchStageLogRepository stageLogRepo;

    @Mock
    private ResearchTeamExecutor teamExecutor;

    private ResearchStageExecutor executor;
    private ResearchRunEntity run;

    @BeforeEach
    void setUp() {
        executor = new ResearchStageExecutor(llmClient, literatureService, stageLogRepo, teamExecutor);
        run = new ResearchRunEntity();
        run.setId(UUID.randomUUID());
        lenient().when(stageLogRepo.save(any(ResearchStageLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stageLogRepo.findByRunIdAndStageNumberAndStatusOrderByCreatedAtAsc(any(UUID.class), anyInt(), eq("running")))
                .thenReturn(List.of());
    }

    @Test
    void literatureScreenProducesWaitingApprovalStageResultWhenPauseIsEnabled() {
        when(llmClient.complete(anyString(), anyString())).thenReturn("{\"approval_notes\":\"screened\"}");

        StageResult result = executor.execute(run, ResearchStage.LITERATURE_SCREEN, "paper context", "single", true);

        assertEquals("waiting_approval", result.status());
        assertTrue(result.requiresApproval());

        ArgumentCaptor<ResearchStageLogEntity> captor = ArgumentCaptor.forClass(ResearchStageLogEntity.class);
        verify(stageLogRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        ResearchStageLogEntity finalLog = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("waiting_approval", finalLog.getStatus());
    }

    @Test
    void literatureScreenAutoModeCompletesWithoutApprovalPause() {
        when(llmClient.complete(anyString(), anyString())).thenReturn("{\"approval_notes\":\"screened\"}");

        StageResult result = executor.execute(run, ResearchStage.LITERATURE_SCREEN, "paper context", "single", false);

        assertEquals("completed", result.status());
        assertTrue(!result.requiresApproval());

        ArgumentCaptor<ResearchStageLogEntity> captor = ArgumentCaptor.forClass(ResearchStageLogEntity.class);
        verify(stageLogRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        ResearchStageLogEntity finalLog = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("completed", finalLog.getStatus());
    }

    @Test
    void exportPublishWrapsMarkdownIntoStructuredPayload() {
        when(llmClient.complete(anyString(), anyString())).thenReturn("# Final Paper");

        StageResult result = executor.execute(run, ResearchStage.EXPORT_PUBLISH, "paper draft", "single", false);

        assertEquals("completed", result.status());
        assertTrue(result.output().contains("\"format\":\"markdown\""));
        assertTrue(result.output().contains("# Final Paper"));
    }

    @Test
    void citationVerifyIncludesSearchEvidenceInOutput() {
        when(literatureService.search(anyString(), anyInt())).thenReturn(List.of(
                new LiteratureResult(
                        "openalex",
                        "W123",
                        "Evidence-backed paper",
                        List.of("Alice", "Bob"),
                        "Abstract",
                        2025,
                        "10.1000/test",
                        "https://example.com/paper",
                        12,
                        Map.of()
                )
        ));
        when(llmClient.complete(anyString(), anyString())).thenReturn("verification complete");

        StageResult result = executor.execute(run, ResearchStage.CITATION_VERIFY, "Evidence-backed paper supports the claim.", "single", false);

        assertEquals("completed", result.status());
        assertTrue(result.output().contains("\"matchCount\":1"));
        assertTrue(result.output().contains("verification complete"));
        verify(literatureService, atLeastOnce()).search(anyString(), anyInt());
    }

    @Test
    void paperDraftSerializesMarkdownBeforePersistingStageLog() {
        when(llmClient.complete(anyString(), anyString())).thenReturn("# Final Draft\n\nThis is markdown output.");
        when(stageLogRepo.save(any(ResearchStageLogEntity.class))).thenAnswer(invocation -> {
            ResearchStageLogEntity entity = invocation.getArgument(0);
            if ("completed".equals(entity.getStatus())) {
                Assertions.assertTrue(entity.getOutputJson().startsWith("{"));
                Assertions.assertTrue(entity.getOutputJson().contains("\"format\":\"markdown\""));
                Assertions.assertTrue(entity.getOutputJson().contains("\"content\":\"# Final Draft"));
            }
            return entity;
        });

        StageResult result = executor.execute(run, ResearchStage.PAPER_DRAFT, "paper context", "single", false);

        assertEquals("completed", result.status());
        ArgumentCaptor<ResearchStageLogEntity> captor = ArgumentCaptor.forClass(ResearchStageLogEntity.class);
        verify(stageLogRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        ResearchStageLogEntity finalLog = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("completed", finalLog.getStatus());
        assertNotNull(finalLog.getOutputJson());
        assertTrue(finalLog.getOutputJson().contains("\"format\":\"markdown\""));
    }

    @Test
    void executeReturnsFailedWhenFailurePersistenceNeedsClearedOutputJson() {
        when(llmClient.complete(anyString(), anyString())).thenReturn("{\"sub_problems\":[\"a\",\"b\"]}");

        AtomicInteger saves = new AtomicInteger();
        when(stageLogRepo.save(any(ResearchStageLogEntity.class))).thenAnswer(invocation -> {
            ResearchStageLogEntity entity = invocation.getArgument(0);
            int call = saves.incrementAndGet();
            if (call == 2) {
                throw new IllegalArgumentException("invalid input syntax for type json");
            }
            if ("failed".equals(entity.getStatus())) {
                assertEquals(null, entity.getOutputJson());
                assertTrue(entity.getErrorMessage().contains("invalid input syntax for type json"));
            }
            return entity;
        });

        StageResult result = executor.execute(run, ResearchStage.PROBLEM_DECOMPOSE, "problem context", "single", false);

        assertEquals("failed", result.status());
        assertTrue(result.errorMessage().contains("invalid input syntax for type json"));

        ArgumentCaptor<ResearchStageLogEntity> captor = ArgumentCaptor.forClass(ResearchStageLogEntity.class);
        verify(stageLogRepo, org.mockito.Mockito.times(3)).save(captor.capture());
        ResearchStageLogEntity failureLog = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("failed", failureLog.getStatus());
        assertEquals(null, failureLog.getOutputJson());
    }

    @Test
    void teamModePersistsFailedStatusAndErrorMessageWhenConsensusReturnsFailure() {
        when(teamExecutor.executeWithConsensus(eq(ResearchStage.TOPIC_INIT), anyString(), anyString()))
                .thenReturn(StageResult.failure("all team models failed"));

        StageResult result = executor.execute(run, ResearchStage.TOPIC_INIT, "topic", "team", false);

        assertEquals("failed", result.status());
        assertTrue(result.errorMessage().contains("all team models failed"));

        ArgumentCaptor<ResearchStageLogEntity> captor = ArgumentCaptor.forClass(ResearchStageLogEntity.class);
        verify(stageLogRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        ResearchStageLogEntity finalLog = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("failed", finalLog.getStatus());
        assertEquals(null, finalLog.getOutputJson());
        assertNotNull(finalLog.getCompletedAt());
        assertTrue(finalLog.getErrorMessage().contains("all team models failed"));
    }

    @Test
    void teamModeTopicInitUsesResearchTopicPrefixInUserContent() {
        when(teamExecutor.executeWithConsensus(eq(ResearchStage.TOPIC_INIT), anyString(), anyString()))
                .thenReturn(StageResult.success("ok"));

        executor.execute(run, ResearchStage.TOPIC_INIT, "my topic", "team", false);

        ArgumentCaptor<String> userContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(teamExecutor).executeWithConsensus(eq(ResearchStage.TOPIC_INIT), anyString(), userContentCaptor.capture());
        assertTrue(userContentCaptor.getValue().startsWith("Research topic: "));
        assertTrue(userContentCaptor.getValue().contains("my topic"));
    }

    @Test
    void teamModeFatalThrowableIsConvertedToFailedStageResult() {
        when(teamExecutor.executeWithConsensus(eq(ResearchStage.TOPIC_INIT), anyString(), anyString()))
                .thenThrow(new AssertionError("team executor crashed"));

        StageResult result = executor.execute(run, ResearchStage.TOPIC_INIT, "topic", "team", false);

        assertEquals("failed", result.status());
        assertTrue(result.errorMessage().contains("AssertionError"));
        assertTrue(result.errorMessage().contains("team executor crashed"));

        ArgumentCaptor<ResearchStageLogEntity> captor = ArgumentCaptor.forClass(ResearchStageLogEntity.class);
        verify(stageLogRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        ResearchStageLogEntity finalLog = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("failed", finalLog.getStatus());
        assertTrue(finalLog.getErrorMessage().contains("AssertionError"));
    }

    @Test
    void executeClosesSupersededRunningLogsBeforeStartingNewAttempt() {
        ResearchStageLogEntity staleLog = new ResearchStageLogEntity();
        staleLog.setRunId(run.getId());
        staleLog.setStageNumber(ResearchStage.LITERATURE_SCREEN.getNumber());
        staleLog.setStageName(ResearchStage.LITERATURE_SCREEN.getKey());
        staleLog.setStatus("running");
        staleLog.setStartedAt(java.time.Instant.now().minusSeconds(15));

        when(stageLogRepo.findByRunIdAndStageNumberAndStatusOrderByCreatedAtAsc(
                run.getId(),
                ResearchStage.LITERATURE_SCREEN.getNumber(),
                "running"
        )).thenReturn(List.of(staleLog));
        when(llmClient.complete(anyString(), anyString())).thenReturn("screened");

        StageResult result = executor.execute(run, ResearchStage.LITERATURE_SCREEN, "paper context", "single", false);

        assertEquals("completed", result.status());
        assertEquals("failed", staleLog.getStatus());
        assertEquals("superseded by resumed stage execution", staleLog.getErrorMessage());
        assertNotNull(staleLog.getCompletedAt());
        assertNotNull(staleLog.getElapsedMs());

        ArgumentCaptor<ResearchStageLogEntity> captor = ArgumentCaptor.forClass(ResearchStageLogEntity.class);
        verify(stageLogRepo, org.mockito.Mockito.times(3)).save(captor.capture());
        assertEquals("failed", captor.getAllValues().get(0).getStatus());
        assertEquals("completed", captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus());
    }
}
