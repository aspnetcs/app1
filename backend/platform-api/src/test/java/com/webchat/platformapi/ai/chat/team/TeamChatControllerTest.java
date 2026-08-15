package com.webchat.platformapi.ai.chat.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.team.dto.CaptainSource;
import com.webchat.platformapi.ai.chat.team.dto.CaptainSelectionMode;
import com.webchat.platformapi.ai.chat.team.dto.DebateEntry;
import com.webchat.platformapi.ai.chat.team.dto.DebateIssue;
import com.webchat.platformapi.ai.chat.team.dto.DebateTurnSession;
import com.webchat.platformapi.ai.chat.team.dto.MemberProposal;
import com.webchat.platformapi.ai.chat.team.dto.TeamConversationContext;
import com.webchat.platformapi.ai.chat.team.dto.TeamTurnEvent;
import com.webchat.platformapi.ai.chat.team.dto.VotingRecord;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TeamChatControllerTest {

    @Mock
    private DebateConsensusService debateService;

    @Mock
    private DebateSessionManager sessionManager;

    @Mock
    private TeamChatRuntimeConfigService runtimeConfigService;

    @Mock
    private TeamConversationPersistenceService persistenceService;

    @Mock
    private RolePolicyService rolePolicyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(runtimeConfigService.snapshot()).thenReturn(new TeamChatRuntimeConfigService.Snapshot(
                true, 5, 1, 60, 120, 8, 2000, 1000, 72, 30
        ));
        TeamChatController controller = new TeamChatController(
                debateService,
                sessionManager,
                runtimeConfigService,
                rolePolicyService,
                persistenceService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void startReturnsFirstTurnMetadataForPollingFallback() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(eq(userId), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(Set.of());
        when(debateService.startConversation(
                eq(userId.toString()),
                eq(List.of("model-a", "model-b")),
                eq(CaptainSelectionMode.AUTO),
                eq(List.of()),
                eq("hello")
        )).thenReturn(new DebateConsensusService.TurnLaunchResult("conv-1", "turn-1", 1));

        mockMvc.perform(
                        post("/api/v1/team-chat/start")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(Map.of(
                                        "message", "hello",
                                        "modelIds", List.of("model-a", "model-b"),
                                        "captainSelectionMode", "auto"
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data.turnId").value("turn-1"))
                .andExpect(jsonPath("$.data.turnNumber").value(1));
    }

    @Test
    void startPassesKnowledgeBaseIdsToDebateService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(eq(userId), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(Set.of());
        when(debateService.startConversation(
                eq(userId.toString()),
                eq(List.of("model-a", "model-b")),
                eq(CaptainSelectionMode.AUTO),
                eq(List.of("kb-1", "kb-2")),
                eq("hello")
        )).thenReturn(new DebateConsensusService.TurnLaunchResult("conv-1", "turn-1", 1));

        mockMvc.perform(
                        post("/api/v1/team-chat/start")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(Map.of(
                                        "message", "hello",
                                        "modelIds", List.of("model-a", "model-b"),
                                        "knowledgeBaseIds", List.of("kb-1", "kb-2"),
                                        "captainSelectionMode", "auto"
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void startRejectsModelsOutsideRolePolicy() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rolePolicyService.resolveAllowedModels(eq(userId), eq("guest")))
                .thenReturn(Set.of("model-a"));

        mockMvc.perform(
                        post("/api/v1/team-chat/start")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "guest")
                                .contentType(APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(Map.of(
                                        "message", "hello",
                                        "modelIds", List.of("model-a", "model-b"),
                                        "captainSelectionMode", "auto"
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("model not available for your role: model-b"));
    }

    @Test
    void startRejectsDuplicateModelIds() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/team-chat/start")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(Map.of(
                                        "message", "hello",
                                        "modelIds", List.of("model-a", "model-a"),
                                        "captainSelectionMode", "auto"
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(23))
                .andExpect(jsonPath("$.message").value("duplicate models are not allowed"));
    }

    @Test
    void statusReturnsMemberStatusesForRestRecovery() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString())))
                .thenReturn(conversation("conv-1", userId.toString()));
        when(sessionManager.loadTurn(eq("turn-1"), eq(userId.toString())))
                .thenReturn(turn("turn-1", "conv-1", userId.toString()));

        mockMvc.perform(
                        get("/api/v1/team-chat/status")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                                .param("turnId", "turn-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.models[0]").value("model-a"))
                .andExpect(jsonPath("$.data.turn.turnId").value("turn-1"))
                .andExpect(jsonPath("$.data.turn.captainExplanation").value("model-a defaulted to captain"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[0].modelId").value("model-a"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[0].proposalStatus").value("completed"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[0].summary").value("model-a proposal"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[0].debateArguments[0].argument").value("model-a argument"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[1].modelId").value("model-b"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[1].proposalStatus").value("timeout"));
    }

    @Test
    void statusFallsBackToActiveTurnWhenTurnIdIsOmitted() throws Exception {
        UUID userId = UUID.randomUUID();
        TeamConversationContext ctx = conversation("conv-1", userId.toString());
        ctx.setActiveTurnId("turn-2");
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString()))).thenReturn(ctx);
        when(sessionManager.loadTurn(eq("turn-2"), eq(userId.toString())))
                .thenReturn(turn("turn-2", "conv-1", userId.toString()));

        mockMvc.perform(
                        get("/api/v1/team-chat/status")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeTurnId").value("turn-2"))
                .andExpect(jsonPath("$.data.turn.turnId").value("turn-2"));
    }

    @Test
    void statusClearsExpiredActiveTurnWhenStoredTurnIsGone() throws Exception {
        UUID userId = UUID.randomUUID();
        TeamConversationContext ctx = conversation("conv-1", userId.toString());
        ctx.setActiveTurnId("turn-2");
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString()))).thenReturn(ctx);
        when(sessionManager.loadTurn(eq("turn-2"), eq(userId.toString())))
                .thenThrow(new IllegalStateException("Debate turn not found or expired: turn-2"));

        mockMvc.perform(
                        get("/api/v1/team-chat/status")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeTurnId").value(nullValue()))
                .andExpect(jsonPath("$.data.turn.status").value("expired_or_not_found"));

        verify(sessionManager).saveConversation(argThat(saved ->
                saved != null
                        && "conv-1".equals(saved.getConversationId())
                        && saved.getActiveTurnId() == null
        ));
    }

    @Test
    void statusFallsBackToPersistedSnapshotWhenRedisConversationExpired() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString())))
                .thenThrow(new IllegalStateException("Team conversation not found or expired: conv-1"));
        when(persistenceService.loadPersistedConversation(eq("conv-1"), eq(userId.toString())))
                .thenReturn(java.util.Optional.of(persistedSnapshot("conv-1")));

        mockMvc.perform(
                        get("/api/v1/team-chat/status")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeTurnId").value(nullValue()))
                .andExpect(jsonPath("$.data.completedTurns").value(1))
                .andExpect(jsonPath("$.data.models[0]").value("model-a"))
                .andExpect(jsonPath("$.data.turn.turnId").value("turn-archived-1"))
                .andExpect(jsonPath("$.data.turn.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.data.turn.finalAnswer").value("final archived answer"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[0].summary").value("archived summary"))
                .andExpect(jsonPath("$.data.turn.memberStatuses[0].debateArguments[0].argument").value("archived argument"))
                .andExpect(jsonPath("$.data.turn.stageTimestamps.COLLECTING").value("2026-04-20T09:59:50Z"))
                .andExpect(jsonPath("$.data.turn.stageTimestamps.COMPLETED").value("2026-04-20T10:00:00Z"));
    }

    @Test
    void historyFallsBackToPersistedSnapshotWhenRedisConversationExpired() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString())))
                .thenThrow(new IllegalStateException("Team conversation not found or expired: conv-1"));
        when(persistenceService.loadPersistedConversation(eq("conv-1"), eq(userId.toString())))
                .thenReturn(java.util.Optional.of(persistedSnapshot("conv-1")));

        mockMvc.perform(
                        get("/api/v1/team-chat/history")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.completedTurns").value(1))
                .andExpect(jsonPath("$.data.decisionHistory[0].turnId").value("turn-archived-1"))
                .andExpect(jsonPath("$.data.decisionHistory[0].captainModelId").value("model-a"))
                .andExpect(jsonPath("$.data.decisionHistory[0].finalAnswerSummary").value("final archived answer"));
    }

    @Test
    void eventsReturnsCursorPageForPollingFallback() throws Exception {
        UUID userId = UUID.randomUUID();
        TeamConversationContext ctx = conversation("conv-1", userId.toString());
        ctx.setActiveTurnId("turn-1");
        DebateTurnSession turn = turn("turn-1", "conv-1", userId.toString());

        TeamTurnEvent event = new TeamTurnEvent();
        event.setCursor(0);
        event.setEvent("team.final_answer_delta");
        event.setData(Map.of("turnId", "turn-1", "delta", "hello"));
        event.setTimestamp(Instant.now());

        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString()))).thenReturn(ctx);
        when(sessionManager.loadTurn(eq("turn-1"), eq(userId.toString()))).thenReturn(turn);
        when(sessionManager.readTurnEvents(eq("turn-1"), eq(userId.toString()), eq(0L), eq(200)))
                .thenReturn(new DebateSessionManager.TurnEventPage(List.of(event), 1L, false, "SYNTHESIZING"));

        mockMvc.perform(
                        get("/api/v1/team-chat/events")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                                .param("turnId", "turn-1")
                                .param("cursor", "0")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.turnId").value("turn-1"))
                .andExpect(jsonPath("$.data.nextCursor").value(1))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.stage").value("SYNTHESIZING"))
                .andExpect(jsonPath("$.data.events[0].event").value("team.final_answer_delta"))
                .andExpect(jsonPath("$.data.events[0].data.delta").value("hello"));
    }

    @Test
    void eventsReturnsIdlePageWhenConversationHasNoActiveTurn() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString())))
                .thenReturn(conversation("conv-1", userId.toString()));

        mockMvc.perform(
                        get("/api/v1/team-chat/events")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.turnId").value(nullValue()))
                .andExpect(jsonPath("$.data.cursor").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(0))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.stage").value("IDLE"))
                .andExpect(jsonPath("$.data.events").isArray());
    }

    @Test
    void eventsClearsExpiredActiveTurnWhenStoredTurnIsGone() throws Exception {
        UUID userId = UUID.randomUUID();
        TeamConversationContext ctx = conversation("conv-1", userId.toString());
        ctx.setActiveTurnId("turn-2");
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString()))).thenReturn(ctx);
        when(sessionManager.loadTurn(eq("turn-2"), eq(userId.toString())))
                .thenThrow(new IllegalStateException("Debate turn not found or expired: turn-2"));

        mockMvc.perform(
                        get("/api/v1/team-chat/events")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.turnId").value("turn-2"))
                .andExpect(jsonPath("$.data.cursor").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(0))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.stage").value("expired_or_not_found"))
                .andExpect(jsonPath("$.data.events").isArray());

        verify(sessionManager).saveConversation(argThat(saved ->
                saved != null
                        && "conv-1".equals(saved.getConversationId())
                        && saved.getActiveTurnId() == null
        ));
    }

    @Test
    void eventsFallsBackToPersistedSnapshotWhenRedisConversationExpired() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessionManager.loadConversation(eq("conv-1"), eq(userId.toString())))
                .thenThrow(new IllegalStateException("Team conversation not found or expired: conv-1"));
        when(persistenceService.loadPersistedConversation(eq("conv-1"), eq(userId.toString())))
                .thenReturn(java.util.Optional.of(persistedSnapshot("conv-1")));

        mockMvc.perform(
                        get("/api/v1/team-chat/events")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .param("conversationId", "conv-1")
                                .param("cursor", "0")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.turnId").value("turn-archived-1"))
                .andExpect(jsonPath("$.data.cursor").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(0))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.stage").value("expired_or_not_found"))
                .andExpect(jsonPath("$.data.events").isArray());
    }

    private static TeamConversationContext conversation(String conversationId, String userId) {
        TeamConversationContext ctx = new TeamConversationContext();
        ctx.setConversationId(conversationId);
        ctx.setUserId(userId);
        ctx.setSelectedModelIds(new ArrayList<>(List.of("model-a", "model-b")));
        ctx.setCaptainSelectionMode(CaptainSelectionMode.AUTO);
        ctx.setCreatedAt(Instant.now());
        ctx.setLastActiveAt(Instant.now());
        return ctx;
    }

    private static DebateTurnSession turn(String turnId, String conversationId, String userId) {
        DebateTurnSession turn = new DebateTurnSession();
        turn.setTurnId(turnId);
        turn.setConversationId(conversationId);
        turn.setUserId(userId);
        turn.setTurnNumber(1);
        DebateIssue issue = new DebateIssue("issue-1", "Issue A", "Issue A description");
        issue.setResolved(true);
        turn.setIssues(List.of(issue));
        java.util.LinkedHashMap<String, String> proposalStatuses = new java.util.LinkedHashMap<>();
        proposalStatuses.put("model-a", "completed");
        proposalStatuses.put("model-b", "timeout");
        turn.setProposalStatuses(proposalStatuses);
        java.util.LinkedHashMap<String, String> debateStatuses = new java.util.LinkedHashMap<>();
        debateStatuses.put("model-a", "pending");
        debateStatuses.put("model-b", "pending");
        turn.setDebateStatuses(debateStatuses);
        turn.setCaptainModelId("model-a");
        turn.setCaptainSource(CaptainSource.AUTO_ELECTED);
        VotingRecord votingRecord = new VotingRecord();
        votingRecord.setDecisionExplanation("model-a defaulted to captain");
        turn.setVotingRecord(votingRecord);
        java.util.LinkedHashMap<String, MemberProposal> proposals = new java.util.LinkedHashMap<>();
        proposals.put("model-a", new MemberProposal("model-a", "model-a proposal"));
        turn.setProposals(proposals);
        DebateEntry debateEntry = new DebateEntry();
        debateEntry.setModelId("model-a");
        debateEntry.setIssueId("issue-1");
        debateEntry.setArgument("model-a argument");
        debateEntry.setStance("support");
        turn.setDebateEntries(List.of(debateEntry));
        return turn;
    }

    private static TeamConversationPersistenceService.PersistedTeamConversationSnapshot persistedSnapshot(String conversationId) {
        return new TeamConversationPersistenceService.PersistedTeamConversationSnapshot(
                conversationId,
                "Persisted team chat",
                List.of("model-a", "model-b"),
                CaptainSelectionMode.AUTO,
                1,
                "shared summary",
                1,
                List.of("model-a"),
                List.of(new TeamConversationPersistenceService.PersistedTeamTurnSnapshot(
                        "turn-archived-1",
                        1,
                        "model-a",
                        CaptainSource.AUTO_ELECTED,
                        "archived question",
                        "final archived answer",
                        List.of(new TeamConversationPersistenceService.PersistedTeamIssue(
                                "issue-1",
                                "Issue A",
                                true
                        )),
                        List.of(new TeamConversationPersistenceService.PersistedTeamMemberStatus(
                                "model-a",
                                "completed",
                                "completed",
                                "archived summary",
                                List.of(new TeamConversationPersistenceService.PersistedTeamDebateArgument(
                                        "issue-1",
                                        "archived argument",
                                        "support"
                                ))
                        )),
                        Instant.parse("2026-04-20T10:00:00Z"),
                        Map.of(
                                "COLLECTING", Instant.parse("2026-04-20T09:59:50Z"),
                                "COMPLETED", Instant.parse("2026-04-20T10:00:00Z")
                        )
                ))
        );
    }
}
