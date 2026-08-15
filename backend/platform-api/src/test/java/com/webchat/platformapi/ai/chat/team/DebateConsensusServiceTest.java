package com.webchat.platformapi.ai.chat.team;

import com.webchat.platformapi.ai.chat.team.dto.CaptainSelectionMode;
import com.webchat.platformapi.ai.chat.team.dto.DebateTurnSession;
import com.webchat.platformapi.ai.chat.team.dto.TeamConversationContext;
import com.webchat.platformapi.ws.WsSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebateConsensusServiceTest {

    @Mock
    private DebateSessionManager sessionManager;

    @Mock
    private TeamLlmClient llmClient;

    @Mock
    private WsSessionRegistry ws;

    @Mock
    private TeamChatRuntimeConfigService runtimeConfigService;

    @Mock
    private TeamConversationPersistenceService persistenceService;

    private DebateConsensusService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void startConversationReturnsPersistedFirstTurnMetadata() {
        lenient().when(runtimeConfigService.snapshot()).thenReturn(defaultConfig());
        Map<String, DebateTurnSession> turnsById = new ConcurrentHashMap<>();
        Map<String, TeamConversationContext> conversationsById = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            DebateTurnSession turn = invocation.getArgument(0);
            turnsById.put(turn.getTurnId(), copyTurn(turn));
            return null;
        }).when(sessionManager).saveTurn(any(DebateTurnSession.class));
        doAnswer(invocation -> {
            TeamConversationContext ctx = invocation.getArgument(0);
            conversationsById.put(ctx.getConversationId(), copyConversation(ctx));
            return null;
        }).when(sessionManager).saveConversation(any(TeamConversationContext.class));
        when(sessionManager.loadTurn(anyString(), anyString())).thenAnswer(invocation ->
                turnsById.get(invocation.getArgument(0)));
        when(sessionManager.loadConversation(anyString(), anyString())).thenAnswer(invocation ->
                conversation(invocation.getArgument(0), invocation.getArgument(1), 0));
        lenient().when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("stop after first async step"));

        service = new DebateConsensusService(sessionManager, llmClient, ws, runtimeConfigService, persistenceService);

        DebateConsensusService.TurnLaunchResult result = service.startConversation(
                "11111111-1111-1111-1111-111111111111",
                List.of("model-a", "model-b"),
                CaptainSelectionMode.AUTO,
                List.of("kb-1", "kb-2"),
                "hello"
        );

        assertNotNull(result);
        DebateTurnSession saved = turnsById.get(result.turnId());
        assertNotNull(saved);
        assertEquals(result.conversationId(), saved.getConversationId());
        assertEquals(1, result.turnNumber());
        assertEquals(1, saved.getTurnNumber());
        assertEquals(result.turnId(), conversationsById.get(result.conversationId()).getActiveTurnId());
        assertEquals(List.of("kb-1", "kb-2"), conversationsById.get(result.conversationId()).getKnowledgeBaseIds());
        assertEquals("pending", saved.getProposalStatuses().get("model-a"));
        assertEquals("pending", saved.getProposalStatuses().get("model-b"));
        verify(persistenceService).ensureConversationRecord(
                eq(result.conversationId()),
                eq("11111111-1111-1111-1111-111111111111"),
                eq(List.of("model-a", "model-b")),
                eq(CaptainSelectionMode.AUTO),
                eq("hello")
        );
    }

    @Test
    void continueConversationReturnsPersistedFollowUpTurnMetadata() {
        lenient().when(runtimeConfigService.snapshot()).thenReturn(defaultConfig());
        Map<String, DebateTurnSession> turnsById = new ConcurrentHashMap<>();
        Map<String, TeamConversationContext> conversationsById = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            DebateTurnSession turn = invocation.getArgument(0);
            turnsById.put(turn.getTurnId(), copyTurn(turn));
            return null;
        }).when(sessionManager).saveTurn(any(DebateTurnSession.class));
        doAnswer(invocation -> {
            TeamConversationContext ctx = invocation.getArgument(0);
            conversationsById.put(ctx.getConversationId(), copyConversation(ctx));
            return null;
        }).when(sessionManager).saveConversation(any(TeamConversationContext.class));
        when(sessionManager.loadTurn(anyString(), anyString())).thenAnswer(invocation ->
                turnsById.get(invocation.getArgument(0)));
        when(sessionManager.loadConversation(anyString(), anyString())).thenAnswer(invocation ->
                conversation(invocation.getArgument(0), invocation.getArgument(1), 1));
        when(sessionManager.tryAcquireConversationLock(eq("conv-1"), anyString(), any())).thenReturn(true);
        lenient().when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("stop after first async step"));

        service = new DebateConsensusService(sessionManager, llmClient, ws, runtimeConfigService, persistenceService);

        DebateConsensusService.TurnLaunchResult result = service.continueConversation(
                "conv-1",
                "11111111-1111-1111-1111-111111111111",
                List.of("kb-3"),
                "follow up"
        );

        assertNotNull(result);
        DebateTurnSession saved = turnsById.get(result.turnId());
        assertNotNull(saved);
        assertEquals("conv-1", result.conversationId());
        assertEquals(result.turnId(), saved.getTurnId());
        assertEquals(2, result.turnNumber());
        assertEquals(2, saved.getTurnNumber());
        assertEquals(result.turnId(), conversationsById.get("conv-1").getActiveTurnId());
        assertEquals(List.of("kb-3"), conversationsById.get("conv-1").getKnowledgeBaseIds());
        assertTrue(saved.getProposalStatuses().containsKey("model-a"));
        assertTrue(saved.getProposalStatuses().containsKey("model-b"));
    }

    @Test
    void continueConversationRestoresPersistedContextWhenRedisConversationExpired() {
        lenient().when(runtimeConfigService.snapshot()).thenReturn(defaultConfig());
        Map<String, DebateTurnSession> turnsById = new ConcurrentHashMap<>();
        Map<String, TeamConversationContext> conversationsById = new ConcurrentHashMap<>();
        TeamConversationContext restored = conversation("conv-1", "11111111-1111-1111-1111-111111111111", 1);

        doAnswer(invocation -> {
            DebateTurnSession turn = invocation.getArgument(0);
            turnsById.put(turn.getTurnId(), copyTurn(turn));
            return null;
        }).when(sessionManager).saveTurn(any(DebateTurnSession.class));
        doAnswer(invocation -> {
            TeamConversationContext ctx = invocation.getArgument(0);
            conversationsById.put(ctx.getConversationId(), copyConversation(ctx));
            return null;
        }).when(sessionManager).saveConversation(any(TeamConversationContext.class));
        when(sessionManager.tryAcquireConversationLock(eq("conv-1"), anyString(), any())).thenReturn(true);
        when(sessionManager.loadConversation(eq("conv-1"), eq("11111111-1111-1111-1111-111111111111")))
                .thenThrow(new IllegalStateException("Team conversation not found or expired: conv-1"));
        when(persistenceService.restoreConversationContext(
                eq("conv-1"),
                eq("11111111-1111-1111-1111-111111111111")
        )).thenReturn(java.util.Optional.of(restored));
        lenient().when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("stop after first async step"));

        service = new DebateConsensusService(sessionManager, llmClient, ws, runtimeConfigService, persistenceService);

        DebateConsensusService.TurnLaunchResult result = service.continueConversation(
                "conv-1",
                "11111111-1111-1111-1111-111111111111",
                List.of("kb-restored"),
                "follow up"
        );

        assertNotNull(result);
        assertEquals("conv-1", result.conversationId());
        assertEquals(2, result.turnNumber());
        verify(persistenceService).restoreConversationContext(
                eq("conv-1"),
                eq("11111111-1111-1111-1111-111111111111")
        );
        verify(sessionManager).saveConversation(any(TeamConversationContext.class));
    }

    @Test
    void continueConversationRejectsWhenActiveTurnIsStillRunning() {
        lenient().when(runtimeConfigService.snapshot()).thenReturn(defaultConfig());
        TeamConversationContext ctx = conversation("conv-1", "11111111-1111-1111-1111-111111111111", 1);
        ctx.setActiveTurnId("turn-1");
        DebateTurnSession activeTurn = new DebateTurnSession();
        activeTurn.setTurnId("turn-1");
        activeTurn.setConversationId("conv-1");
        activeTurn.setUserId("11111111-1111-1111-1111-111111111111");

        when(sessionManager.tryAcquireConversationLock(eq("conv-1"), anyString(), any())).thenReturn(true);
        when(sessionManager.loadConversation(eq("conv-1"), eq("11111111-1111-1111-1111-111111111111")))
                .thenReturn(ctx);
        when(sessionManager.loadTurn(eq("turn-1"), eq("11111111-1111-1111-1111-111111111111")))
                .thenReturn(activeTurn);

        service = new DebateConsensusService(sessionManager, llmClient, ws, runtimeConfigService, persistenceService);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.continueConversation(
                        "conv-1",
                        "11111111-1111-1111-1111-111111111111",
                        List.of(),
                        "follow up"
                ));

        assertEquals("team conversation already has an active turn", error.getMessage());
        verify(sessionManager, never()).saveTurn(any(DebateTurnSession.class));
    }

    @Test
    void completedTurnIsPersistedAfterConversationContextFinalization() throws Exception {
        when(runtimeConfigService.snapshot()).thenReturn(defaultConfig());
        Map<String, DebateTurnSession> turnsById = new ConcurrentHashMap<>();
        Map<String, TeamConversationContext> conversationsById = new ConcurrentHashMap<>();
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch completedSaved = new CountDownLatch(1);
        CountDownLatch completedConversationSaved = new CountDownLatch(1);

        doAnswer(invocation -> {
            DebateTurnSession turn = invocation.getArgument(0);
            DebateTurnSession copy = copyTurn(turn);
            copy.setStage(turn.getStage());
            copy.setFinalAnswer(turn.getFinalAnswer());
            copy.setCaptainModelId(turn.getCaptainModelId());
            copy.setCaptainSource(turn.getCaptainSource());
            copy.setIssues(new ArrayList<>(turn.getIssues()));
            turnsById.put(turn.getTurnId(), copy);
            events.add("turn:" + turn.getStage().name());
            if (turn.getStage() == com.webchat.platformapi.ai.chat.team.dto.DebateStage.COMPLETED) {
                completedSaved.countDown();
            }
            return null;
        }).when(sessionManager).saveTurn(any(DebateTurnSession.class));
        doAnswer(invocation -> {
            TeamConversationContext ctx = invocation.getArgument(0);
            TeamConversationContext copy = copyConversation(ctx);
            copy.setSharedSummary(ctx.getSharedSummary());
            copy.setCaptainHandoffSummary(ctx.getCaptainHandoffSummary());
            copy.setCaptainHistory(new ArrayList<>(ctx.getCaptainHistory()));
            copy.setDecisionHistory(new ArrayList<>(ctx.getDecisionHistory()));
            conversationsById.put(ctx.getConversationId(), copy);
            events.add("conversation:" + ctx.getCompletedTurns());
            if (ctx.getCompletedTurns() == 1) {
                completedConversationSaved.countDown();
            }
            return null;
        }).when(sessionManager).saveConversation(any(TeamConversationContext.class));
        when(sessionManager.loadTurn(anyString(), anyString())).thenAnswer(invocation ->
                turnsById.get(invocation.getArgument(0)));
        when(sessionManager.loadConversation(anyString(), anyString())).thenAnswer(invocation -> {
            TeamConversationContext existing = conversationsById.get(invocation.getArgument(0));
            return existing != null ? copyConversation(existing) : conversation(invocation.getArgument(0), invocation.getArgument(1), 0);
        });
        when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyList())).thenReturn(
                new TeamLlmClient.TeamLlmResult("proposal-a", 11, 22, 30, 1L, "openai", "model-a"),
                new TeamLlmClient.TeamLlmResult("proposal-b", 11, 22, 30, 1L, "openai", "model-b")
        );
        when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyInt(), anyDouble(), anyList())).thenReturn(
                new TeamLlmClient.TeamLlmResult("[]", 12, 6, 30, 1L, "openai", "model-a"),
                new TeamLlmClient.TeamLlmResult("compressed-summary", 8, 4, 30, 1L, "openai", "model-a")
        );
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<String> onDelta = invocation.getArgument(7);
            onDelta.accept("final-answer");
            return new TeamLlmClient.TeamLlmResult("final-answer", 20, 10, 50, 1L, "openai", "model-a");
        }).when(llmClient).completeStreaming(any(UUID.class), anyString(), anyString(), anyString(), anyInt(), anyDouble(), anyList(), any());

        service = new DebateConsensusService(sessionManager, llmClient, ws, runtimeConfigService, persistenceService);

        DebateConsensusService.TurnLaunchResult result = service.startConversation(
                "11111111-1111-1111-1111-111111111111",
                List.of("model-a", "model-b"),
                CaptainSelectionMode.FIXED_FIRST,
                List.of(),
                "hello"
        );

        assertTrue(completedSaved.await(5, TimeUnit.SECONDS));
        assertTrue(completedConversationSaved.await(5, TimeUnit.SECONDS));
        int conversationSaveIndex = events.indexOf("conversation:1");
        int completedTurnIndex = events.indexOf("turn:COMPLETED");

        assertThat(conversationSaveIndex).isGreaterThanOrEqualTo(0);
        assertThat(completedTurnIndex).isGreaterThanOrEqualTo(0);
        assertThat(conversationsById.get(result.conversationId()).getCompletedTurns()).isEqualTo(1);
        assertThat(turnsById.get(result.turnId()).getStage()).isEqualTo(com.webchat.platformapi.ai.chat.team.dto.DebateStage.COMPLETED);
        verify(persistenceService).persistCompletedTurn(any(TeamConversationContext.class), any(DebateTurnSession.class));
    }

    @Test
    void debateStageHonorsConfiguredMaxRounds() throws Exception {
        TeamChatRuntimeConfigService.Snapshot config = new TeamChatRuntimeConfigService.Snapshot(
                true,
                5,
                2,
                60,
                120,
                8,
                2000,
                1000,
                72,
                30
        );
        when(runtimeConfigService.snapshot()).thenReturn(config);

        Map<String, DebateTurnSession> turnsById = new ConcurrentHashMap<>();
        Map<String, TeamConversationContext> conversationsById = new ConcurrentHashMap<>();
        CountDownLatch completedSaved = new CountDownLatch(1);

        doAnswer(invocation -> {
            DebateTurnSession turn = invocation.getArgument(0);
            DebateTurnSession copy = copyTurn(turn);
            copy.setStage(turn.getStage());
            copy.setFinalAnswer(turn.getFinalAnswer());
            copy.setCaptainModelId(turn.getCaptainModelId());
            copy.setCaptainSource(turn.getCaptainSource());
            copy.setIssues(new ArrayList<>(turn.getIssues()));
            copy.setDebateEntries(new ArrayList<>(turn.getDebateEntries()));
            turnsById.put(turn.getTurnId(), copy);
            if (turn.getStage() == com.webchat.platformapi.ai.chat.team.dto.DebateStage.COMPLETED) {
                completedSaved.countDown();
            }
            return null;
        }).when(sessionManager).saveTurn(any(DebateTurnSession.class));
        doAnswer(invocation -> {
            TeamConversationContext ctx = invocation.getArgument(0);
            conversationsById.put(ctx.getConversationId(), copyConversation(ctx));
            return null;
        }).when(sessionManager).saveConversation(any(TeamConversationContext.class));
        when(sessionManager.loadTurn(anyString(), anyString())).thenAnswer(invocation ->
                turnsById.get(invocation.getArgument(0)));
        when(sessionManager.loadConversation(anyString(), anyString())).thenAnswer(invocation -> {
            TeamConversationContext existing = conversationsById.get(invocation.getArgument(0));
            return existing != null ? copyConversation(existing) : conversation(invocation.getArgument(0), invocation.getArgument(1), 0);
        });
        when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyList())).thenReturn(
                new TeamLlmClient.TeamLlmResult("proposal-a", 11, 22, 30, 1L, "openai", "model-a"),
                new TeamLlmClient.TeamLlmResult("proposal-b", 11, 22, 30, 1L, "openai", "model-b")
        );
        when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyInt(), anyDouble(), anyList())).thenReturn(
                new TeamLlmClient.TeamLlmResult("[{\"issueId\":\"issue-1\",\"title\":\"Conflict\",\"description\":\"Resolve disagreement\",\"resolved\":false}]", 9, 5, 20, 1L, "openai", "model-a"),
                new TeamLlmClient.TeamLlmResult("{\"stance\":\"A1\",\"argument\":\"First round a\",\"stanceChanged\":false}", 9, 5, 20, 1L, "openai", "model-a"),
                new TeamLlmClient.TeamLlmResult("{\"stance\":\"B1\",\"argument\":\"First round b\",\"stanceChanged\":false}", 9, 5, 20, 1L, "openai", "model-b"),
                new TeamLlmClient.TeamLlmResult("{\"stance\":\"A2\",\"argument\":\"Second round a\",\"stanceChanged\":true}", 9, 5, 20, 1L, "openai", "model-a"),
                new TeamLlmClient.TeamLlmResult("{\"stance\":\"B2\",\"argument\":\"Second round b\",\"stanceChanged\":true}", 9, 5, 20, 1L, "openai", "model-b"),
                new TeamLlmClient.TeamLlmResult("compressed-summary", 8, 4, 30, 1L, "openai", "model-a")
        );
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<String> onDelta = invocation.getArgument(7);
            onDelta.accept("final-answer");
            return new TeamLlmClient.TeamLlmResult("final-answer", 20, 10, 50, 1L, "openai", "model-a");
        }).when(llmClient).completeStreaming(any(UUID.class), anyString(), anyString(), anyString(), anyInt(), anyDouble(), anyList(), any());

        service = new DebateConsensusService(sessionManager, llmClient, ws, runtimeConfigService, persistenceService);

        DebateConsensusService.TurnLaunchResult result = service.startConversation(
                "11111111-1111-1111-1111-111111111111",
                List.of("model-a", "model-b"),
                CaptainSelectionMode.FIXED_FIRST,
                List.of(),
                "hello"
        );

        assertTrue(completedSaved.await(5, TimeUnit.SECONDS));
        DebateTurnSession completedTurn = turnsById.get(result.turnId());

        assertThat(completedTurn.getDebateEntries()).hasSize(4);
        assertThat(completedTurn.getDebateEntries().stream().map(com.webchat.platformapi.ai.chat.team.dto.DebateEntry::getRound))
                .containsExactly(1, 1, 2, 2);
    }

    @Test
    void autoVotingPublishesCaptainElectionWhenOnlyOneCandidateProposalSucceeds() throws Exception {
        when(runtimeConfigService.snapshot()).thenReturn(defaultConfig());
        Map<String, DebateTurnSession> turnsById = new ConcurrentHashMap<>();
        Map<String, TeamConversationContext> conversationsById = new ConcurrentHashMap<>();
        CountDownLatch captainElectionPublished = new CountDownLatch(1);

        doAnswer(invocation -> {
            DebateTurnSession turn = invocation.getArgument(0);
            DebateTurnSession copy = copyTurn(turn);
            copy.setStage(turn.getStage());
            copy.setCaptainModelId(turn.getCaptainModelId());
            copy.setCaptainSource(turn.getCaptainSource());
            copy.setVotingRecord(turn.getVotingRecord());
            turnsById.put(turn.getTurnId(), copy);
            return null;
        }).when(sessionManager).saveTurn(any(DebateTurnSession.class));
        doAnswer(invocation -> {
            TeamConversationContext ctx = invocation.getArgument(0);
            conversationsById.put(ctx.getConversationId(), copyConversation(ctx));
            return null;
        }).when(sessionManager).saveConversation(any(TeamConversationContext.class));
        doAnswer(invocation -> {
            String event = invocation.getArgument(1);
            if ("team.captain_elected".equals(event)) {
                captainElectionPublished.countDown();
            }
            return null;
        }).when(sessionManager).appendTurnEvent(anyString(), anyString(), any());
        when(sessionManager.loadTurn(anyString(), anyString())).thenAnswer(invocation ->
                turnsById.get(invocation.getArgument(0)));
        when(sessionManager.loadConversation(anyString(), anyString())).thenAnswer(invocation -> {
            TeamConversationContext existing = conversationsById.get(invocation.getArgument(0));
            return existing != null ? copyConversation(existing) : conversation(invocation.getArgument(0), invocation.getArgument(1), 0);
        });
        doAnswer(invocation -> {
            String modelId = invocation.getArgument(1);
            if ("model-a".equals(modelId)) {
                return new TeamLlmClient.TeamLlmResult("proposal-a", 11, 22, 30, 1L, "openai", modelId);
            }
            throw new RuntimeException("proposal failed");
        }).when(llmClient).complete(any(UUID.class), anyString(), anyString(), anyString(), anyList());
        when(llmClient.complete(any(UUID.class), anyString(), anyString(), anyString(), anyInt(), anyDouble(), anyList())).thenReturn(
                new TeamLlmClient.TeamLlmResult("[]", 9, 5, 20, 1L, "openai", "model-a"),
                new TeamLlmClient.TeamLlmResult("compressed-summary", 8, 4, 30, 1L, "openai", "model-a")
        );

        service = new DebateConsensusService(sessionManager, llmClient, ws, runtimeConfigService, persistenceService);

        service.startConversation(
                "11111111-1111-1111-1111-111111111111",
                List.of("model-a", "model-b"),
                CaptainSelectionMode.AUTO,
                List.of(),
                "hello"
        );

        assertTrue(captainElectionPublished.await(5, TimeUnit.SECONDS));
    }

    private static TeamConversationContext conversation(String conversationId, String userId, int completedTurns) {
        TeamConversationContext ctx = new TeamConversationContext();
        ctx.setConversationId(conversationId);
        ctx.setUserId(userId);
        ctx.setSelectedModelIds(new ArrayList<>(List.of("model-a", "model-b")));
        ctx.setKnowledgeBaseIds(new ArrayList<>(List.of("kb-1")));
        ctx.setCaptainSelectionMode(CaptainSelectionMode.AUTO);
        ctx.setCompletedTurns(completedTurns);
        ctx.setCreatedAt(Instant.now());
        ctx.setLastActiveAt(Instant.now());
        return ctx;
    }

    private static DebateTurnSession copyTurn(DebateTurnSession source) {
        DebateTurnSession copy = new DebateTurnSession();
        copy.setTurnId(source.getTurnId());
        copy.setConversationId(source.getConversationId());
        copy.setUserId(source.getUserId());
        copy.setTurnNumber(source.getTurnNumber());
        copy.setUserMessage(source.getUserMessage());
        copy.setCaptainSelectionMode(source.getCaptainSelectionMode());
        copy.setProposalStatuses(new ConcurrentHashMap<>(source.getProposalStatuses()));
        copy.setDebateStatuses(new ConcurrentHashMap<>(source.getDebateStatuses()));
        return copy;
    }

    private static TeamConversationContext copyConversation(TeamConversationContext source) {
        TeamConversationContext copy = new TeamConversationContext();
        copy.setConversationId(source.getConversationId());
        copy.setUserId(source.getUserId());
        copy.setSelectedModelIds(new ArrayList<>(source.getSelectedModelIds()));
        copy.setKnowledgeBaseIds(new ArrayList<>(source.getKnowledgeBaseIds()));
        copy.setCaptainSelectionMode(source.getCaptainSelectionMode());
        copy.setCompletedTurns(source.getCompletedTurns());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setLastActiveAt(source.getLastActiveAt());
        copy.setActiveTurnId(source.getActiveTurnId());
        return copy;
    }

    private static TeamChatRuntimeConfigService.Snapshot defaultConfig() {
        return new TeamChatRuntimeConfigService.Snapshot(
                true,
                5,
                1,
                60,
                120,
                8,
                2000,
                1000,
                72,
                30
        );
    }
}
