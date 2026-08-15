package com.webchat.platformapi.research.pipeline;

import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.research.ResearchStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchTeamExecutorTest {

    private static ResearchTeamExecutor executorWithTimeout(
            ResearchLlmClient llmClient,
            ChannelRouter channelRouter,
            long timeoutSeconds
    ) {
        return new ResearchTeamExecutor(llmClient, channelRouter) {
            @Override
            long modelCallTimeoutSeconds() {
                return timeoutSeconds;
            }
        };
    }

    @Test
    void resolveTeamModelsCollectsFromChannelModelsAndMappingKeys() {
        ResearchLlmClient llmClient = mock(ResearchLlmClient.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);

        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("a, b");
        channel.setModelMapping(Map.of("c", "c"));
        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));

        ResearchTeamExecutor executor = new ResearchTeamExecutor(llmClient, channelRouter);
        List<String> models = executor.resolveTeamModels(4);

        assertTrue(models.contains("a"));
        assertTrue(models.contains("b"));
        assertTrue(models.contains("c"));
    }

    @Test
    void executeWithConsensusFallsBackToSingleWhenAllExpertsFail() {
        ResearchLlmClient llmClient = mock(ResearchLlmClient.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);

        ResearchTeamExecutor executor = Mockito.spy(executorWithTimeout(llmClient, channelRouter, 180));
        doReturn(List.of("m1", "m2", "m3")).when(executor).resolveTeamModels(3);

        String systemPrompt = "system";
        String userContent = "user";

        when(llmClient.complete(systemPrompt, userContent, "m1", 4096, 0.4))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn("fallback");
        when(llmClient.complete(systemPrompt, userContent, "m2", 4096, 0.4))
                .thenThrow(new RuntimeException("boom"));
        when(llmClient.complete(systemPrompt, userContent, "m3", 4096, 0.4))
                .thenThrow(new RuntimeException("boom"));

        StageResult result = executor.executeWithConsensus(ResearchStage.TOPIC_INIT, systemPrompt, userContent);

        assertTrue(result.isSuccess());
        assertEquals("fallback", result.output());
        assertFalse(result.requiresApproval());
        assertNull(result.errorMessage());

        // Phase 1: all models attempted; fallback uses the first model again.
        verify(llmClient, times(2)).complete(systemPrompt, userContent, "m1", 4096, 0.4);
        verify(llmClient, times(1)).complete(systemPrompt, userContent, "m2", 4096, 0.4);
        verify(llmClient, times(1)).complete(systemPrompt, userContent, "m3", 4096, 0.4);
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    @Test
    void executeWithConsensusReturnsSingleProposalWithoutJudgeWhenOnlyOneExpertSucceeds() {
        ResearchLlmClient llmClient = mock(ResearchLlmClient.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);

        ResearchTeamExecutor executor = Mockito.spy(executorWithTimeout(llmClient, channelRouter, 180));
        doReturn(List.of("m1", "m2", "m3")).when(executor).resolveTeamModels(3);

        String systemPrompt = "system";
        String userContent = "user";

        when(llmClient.complete(systemPrompt, userContent, "m1", 4096, 0.4))
                .thenReturn("proposal-1");
        when(llmClient.complete(systemPrompt, userContent, "m2", 4096, 0.4))
                .thenThrow(new RuntimeException("boom"));
        when(llmClient.complete(systemPrompt, userContent, "m3", 4096, 0.4))
                .thenThrow(new RuntimeException("boom"));

        StageResult result = executor.executeWithConsensus(ResearchStage.TOPIC_INIT, systemPrompt, userContent);

        assertTrue(result.isSuccess());
        assertEquals("proposal-1", result.output());
        assertFalse(result.requiresApproval());
        assertNull(result.errorMessage());

        // No judge synthesis in single-proposal mode.
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    @Test
    void executeWithConsensusRunsJudgeSynthesisWhenMultipleProposals() {
        ResearchLlmClient llmClient = mock(ResearchLlmClient.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);

        ResearchTeamExecutor executor = Mockito.spy(executorWithTimeout(llmClient, channelRouter, 180));
        doReturn(List.of("m1", "m2", "m3")).when(executor).resolveTeamModels(3);

        String systemPrompt = "system";
        String userContent = "user";

        when(llmClient.complete(systemPrompt, userContent, "m1", 4096, 0.4))
                .thenReturn("proposal-1");
        when(llmClient.complete(systemPrompt, userContent, "m2", 4096, 0.4))
                .thenReturn("proposal-2");
        when(llmClient.complete(systemPrompt, userContent, "m3", 4096, 0.4))
                .thenThrow(new RuntimeException("boom"));

        when(llmClient.complete(anyString(), eq(userContent))).thenReturn("final");

        StageResult result = executor.executeWithConsensus(ResearchStage.TOPIC_INIT, systemPrompt, userContent);

        assertTrue(result.isSuccess());
        assertEquals("final", result.output());
        assertFalse(result.requiresApproval());
        assertNull(result.errorMessage());

        ArgumentCaptor<String> synthesisPrompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(1)).complete(synthesisPrompt.capture(), eq(userContent));
        assertTrue(synthesisPrompt.getValue().contains("topic_init"));
        assertTrue(synthesisPrompt.getValue().contains("proposal-1"));
        assertTrue(synthesisPrompt.getValue().contains("proposal-2"));
    }

    @Test
    void executeWithConsensusReturnsWaitingApprovalWhenStageIsGate() {
        ResearchLlmClient llmClient = mock(ResearchLlmClient.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);

        ResearchTeamExecutor executor = Mockito.spy(executorWithTimeout(llmClient, channelRouter, 180));
        doReturn(List.of("m1")).when(executor).resolveTeamModels(3);

        String systemPrompt = "system";
        String userContent = "user";

        when(llmClient.complete(systemPrompt, userContent, "m1", 4096, 0.4))
                .thenReturn("gate-output");

        StageResult result = executor.executeWithConsensus(ResearchStage.LITERATURE_SCREEN, systemPrompt, userContent);

        assertEquals("waiting_approval", result.status());
        assertEquals("gate-output", result.output());
        assertTrue(result.requiresApproval());
        assertNull(result.errorMessage());
    }

    @Test
    void executeWithConsensusFallsBackToFirstProposalWhenJudgeSynthesisTimesOut() {
        ResearchLlmClient llmClient = mock(ResearchLlmClient.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);

        ResearchTeamExecutor executor = Mockito.spy(executorWithTimeout(llmClient, channelRouter, 1));
        doReturn(List.of("m1", "m2", "m3")).when(executor).resolveTeamModels(3);

        String systemPrompt = "system";
        String userContent = "user";

        when(llmClient.complete(systemPrompt, userContent, "m1", 4096, 0.4))
                .thenReturn("proposal-1");
        when(llmClient.complete(systemPrompt, userContent, "m2", 4096, 0.4))
                .thenReturn("proposal-2");
        when(llmClient.complete(systemPrompt, userContent, "m3", 4096, 0.4))
                .thenThrow(new RuntimeException("boom"));
        when(llmClient.complete(anyString(), eq(userContent))).thenAnswer(invocation -> {
            Thread.sleep(1200);
            return "final";
        });

        StageResult result = executor.executeWithConsensus(ResearchStage.TOPIC_INIT, systemPrompt, userContent);

        assertTrue(result.isSuccess());
        assertEquals("proposal-1", result.output());
    }
}
