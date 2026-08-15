package com.webchat.platformapi.ai.chat.team;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.team.TeamLlmClient.TeamLlmResult;
import com.webchat.platformapi.ai.chat.team.dto.*;
import com.webchat.platformapi.ws.WsSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core five-stage debate consensus engine for team conversations.
 * <p>
 * Orchestrates: COLLECTING -> VOTING -> EXTRACTING -> DEBATING -> SYNTHESIZING
 * <p>
 * Design constraints:
 * <ul>
 *   <li>All state is persisted in Redis via DebateSessionManager (no in-memory state)</li>
 *   <li>Captain is a per-turn temporary role, never persisted as permanent identity</li>
 *   <li>Reasoning text from thinking models never enters long-term memory</li>
 *   <li>Scheme C voting: 1 judge initial ranking + 2 Position Flip verifications</li>
 * </ul>
 */
@Service
public class DebateConsensusService {

    private static final Logger log = LoggerFactory.getLogger(DebateConsensusService.class);
    private static final int FINAL_ANSWER_FLUSH_CHARS = 500;

    private final DebateSessionManager sessionManager;
    private final TeamLlmClient llmClient;
    private final WsSessionRegistry ws;
    private final TeamChatRuntimeConfigService runtimeConfigService;
    private final TeamConversationPersistenceService persistenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService orchestrator = newNamedExecutor("team-chat-orchestrator");
    private final ExecutorService llmExecutor = newNamedExecutor("team-chat-llm");
    private final AdjustableSemaphore llmConcurrency = new AdjustableSemaphore(8);
    private volatile int llmConcurrencyLimit = 8;

    public DebateConsensusService(
            DebateSessionManager sessionManager,
            TeamLlmClient llmClient,
            WsSessionRegistry ws,
            TeamChatRuntimeConfigService runtimeConfigService,
            TeamConversationPersistenceService persistenceService
    ) {
        this.sessionManager = sessionManager;
        this.llmClient = llmClient;
        this.ws = ws;
        this.runtimeConfigService = runtimeConfigService;
        this.persistenceService = persistenceService;
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        orchestrator.shutdown();
        try {
            if (!orchestrator.awaitTermination(10, TimeUnit.SECONDS)) {
                orchestrator.shutdownNow();
            }
            llmExecutor.shutdown();
            if (!llmExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                llmExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            orchestrator.shutdownNow();
            llmExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========== Public Entry Points ==========

    public record TurnLaunchResult(String conversationId, String turnId, int turnNumber) {}

    /**
     * Start a new team conversation and execute the first debate turn.
     *
     * @return the new conversationId and first turn metadata
     */
    public TurnLaunchResult startConversation(String userId, List<String> modelIds,
                                              CaptainSelectionMode captainMode,
                                              List<String> knowledgeBaseIds,
                                              String userMessage) {
        String conversationId = UUID.randomUUID().toString();

        TeamConversationContext ctx = new TeamConversationContext();
        ctx.setConversationId(conversationId);
        ctx.setUserId(userId);
        ctx.setSelectedModelIds(new ArrayList<>(modelIds));
        ctx.setKnowledgeBaseIds(copyKnowledgeBaseIds(knowledgeBaseIds));
        ctx.setCaptainSelectionMode(captainMode);
        ctx.setCreatedAt(Instant.now());
        ctx.setLastActiveAt(Instant.now());
        ctx.setMemoryVersion(0);
        ctx.setCompletedTurns(0);

        TurnLaunchResult launch = createPendingTurn(conversationId, userId, captainMode, modelIds, userMessage, 1);
        persistenceService.ensureConversationRecord(
                conversationId,
                userId,
                modelIds,
                captainMode,
                buildConversationTitle(userMessage)
        );
        ctx.setActiveTurnId(launch.turnId());
        sessionManager.saveConversation(ctx);

        // Execute first turn asynchronously
        orchestrator.submit(() -> {
            try {
                executeTurn(conversationId, userId, userMessage, launch.turnId(), launch.turnNumber());
            } catch (Exception e) {
                log.error("[debate] first turn failed: conversationId={}, error={}",
                        conversationId, e.getMessage(), e);
            }
        });

        return launch;
    }

    /**
     * Continue an existing team conversation with a follow-up question.
     *
     * @return the new turn metadata
     */
    public TurnLaunchResult continueConversation(String conversationId, String userId,
                                                 List<String> knowledgeBaseIds,
                                                 String userMessage) {
        String lockToken = UUID.randomUUID().toString();
        boolean locked = sessionManager.tryAcquireConversationLock(
                conversationId,
                lockToken,
                Duration.ofSeconds(15)
        );
        if (!locked) {
            throw new IllegalStateException("team conversation is busy");
        }

        try {
            // Validate ownership
            TeamConversationContext ctx = loadConversationOrRestore(conversationId, userId);
            if (sessionManager.conversationExists(conversationId)) {
                sessionManager.refreshConversationTtl(conversationId);
            }
            ensureReadyForNextTurn(ctx, userId);
            if (knowledgeBaseIds != null) {
                ctx.setKnowledgeBaseIds(copyKnowledgeBaseIds(knowledgeBaseIds));
            }
            int turnNumber = ctx.getCompletedTurns() + 1;
            TurnLaunchResult launch = createPendingTurn(
                    conversationId,
                    userId,
                    ctx.getCaptainSelectionMode(),
                    ctx.getSelectedModelIds(),
                    userMessage,
                    turnNumber
            );
            ctx.setActiveTurnId(launch.turnId());
            ctx.setLastActiveAt(Instant.now());
            sessionManager.saveConversation(ctx);

            orchestrator.submit(() -> {
                try {
                    executeTurn(conversationId, userId, userMessage, launch.turnId(), launch.turnNumber());
                } catch (Exception e) {
                    log.error("[debate] follow-up turn failed: conversationId={}, error={}",
                            conversationId, e.getMessage(), e);
                }
            });

            return launch;
        } finally {
            sessionManager.releaseConversationLock(conversationId, lockToken);
        }
    }

    // ========== Core Orchestration ==========

    /**
     * Execute a single five-stage debate turn.
     */
    private TurnLaunchResult createPendingTurn(String conversationId,
                                               String userId,
                                               CaptainSelectionMode captainMode,
                                               List<String> modelIds,
                                               String userMessage,
                                               int turnNumber) {
        String turnId = UUID.randomUUID().toString();

        DebateTurnSession turn = new DebateTurnSession();
        turn.setTurnId(turnId);
        turn.setConversationId(conversationId);
        turn.setUserId(userId);
        turn.setTurnNumber(turnNumber);
        turn.setUserMessage(userMessage);
        turn.setCaptainSelectionMode(captainMode);
        turn.getStageTimestamps().put(DebateStage.COLLECTING.name(), Instant.now());
        for (String modelId : modelIds) {
            turn.getProposalStatuses().put(modelId, "pending");
            turn.getDebateStatuses().put(modelId, "pending");
        }
        sessionManager.saveTurn(turn);
        return new TurnLaunchResult(conversationId, turnId, turnNumber);
    }

    private void ensureReadyForNextTurn(TeamConversationContext ctx, String userId) {
        String activeTurnId = ctx.getActiveTurnId();
        if (activeTurnId == null || activeTurnId.isBlank()) {
            return;
        }

        try {
            DebateTurnSession activeTurn = sessionManager.loadTurn(activeTurnId, userId);
            DebateStage stage = activeTurn.getStage();
            if (stage != DebateStage.COMPLETED && stage != DebateStage.FAILED) {
                throw new IllegalStateException("team conversation already has an active turn");
            }
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("Debate turn not found or expired")) {
                ctx.setActiveTurnId(null);
                sessionManager.saveConversation(ctx);
                return;
            }
            throw e;
        }
    }

    private void executeTurn(String conversationId, String userId, String userMessage, String turnId, int turnNumber) {
        TeamConversationContext ctx = sessionManager.loadConversation(conversationId, userId);
        DebateTurnSession turn = sessionManager.loadTurn(turnId, userId);
        turn.setUserMessage(userMessage);
        turn.setCaptainSelectionMode(ctx.getCaptainSelectionMode());
        sessionManager.saveTurn(turn);

        UUID userUuid = UUID.fromString(userId);

        // Notify frontend: turn started
        publishTurnEvent(turnId, userUuid, "team.turn_started", Map.of(
                "conversationId", conversationId,
                "turnId", turnId,
                "turnNumber", turnNumber,
                "stage", DebateStage.COLLECTING.name(),
                "models", ctx.getSelectedModelIds()
        ));

        try {
            stageCollecting(turn, ctx);
        } catch (Exception e) {
            failTurn(turn, conversationId, userUuid, e);
            return;
        }

        try {
            stageVoting(turn, ctx, userUuid);
        } catch (Exception e) {
            log.warn("[debate] VOTING failed, falling back to fixed_first: turnId={}, error={}",
                    turnId, e.getMessage());
            applyVotingFallback(turn, ctx, userUuid);
        }

        try {
            stageExtracting(turn, ctx, userUuid);
        } catch (Exception e) {
            log.warn("[debate] EXTRACTING failed, skipping debate: turnId={}, error={}",
                    turnId, e.getMessage());
            turn.setIssues(new ArrayList<>());
            sessionManager.saveTurn(turn);
            publishTurnEvent(turnId, userUuid, "team.issues_extracted", Map.of(
                    "turnId", turnId,
                    "issueCount", 0,
                    "issues", List.of()
            ));
        }

        try {
            stageDebating(turn, ctx, userUuid);
        } catch (Exception e) {
            log.warn("[debate] DEBATING failed unexpectedly: turnId={}, error={}",
                    turnId, e.getMessage());
        }

        try {
            stageSynthesizing(turn, ctx, userUuid);
        } catch (Exception e) {
            failTurn(turn, conversationId, userUuid, e);
            return;
        }

        persistCompletedTurn(turn, ctx);
        publishTurnEvent(turnId, userUuid, "team.turn_completed", Map.of(
                "conversationId", conversationId,
                "turnId", turnId,
                "finalAnswer", turn.getFinalAnswer() != null ? turn.getFinalAnswer() : "",
                "captainModelId", turn.getCaptainModelId() != null ? turn.getCaptainModelId() : "",
                "captainSource", turn.getCaptainSource() != null ? turn.getCaptainSource().name() : ""
        ));

        orchestrator.submit(() -> finalizeTurnAsync(turn.getConversationId(), turn.getTurnId(), turn.getUserId()));
    }

    private void failTurn(DebateTurnSession turn, String conversationId, UUID userUuid, Exception e) {
            log.error("[debate] turn failed at stage {}: turnId={}, error={}",
                    turn.getStage(), turn.getTurnId(), e.getMessage(), e);
            turn.fail(e.getMessage());
            sessionManager.saveTurn(turn);

            publishTurnEvent(turn.getTurnId(), userUuid, "team.turn_failed", Map.of(
                    "conversationId", conversationId,
                    "turnId", turn.getTurnId(),
                    "stage", turn.getStage().name(),
                    "error", e.getMessage() != null ? e.getMessage() : "unknown error",
                    "memberStatuses", buildMemberStatuses(turn),
                    "failedModels", List.copyOf(turn.getFailedModels())
            ));
    }

    // ========== Stage 1: COLLECTING ==========

    /**
     * All models generate independent proposals in parallel.
     */
    private void stageCollecting(DebateTurnSession turn, TeamConversationContext ctx) {
        UUID userUuid = UUID.fromString(turn.getUserId());
        List<String> modelIds = ctx.getSelectedModelIds();

        publishTurnEvent(turn.getTurnId(), userUuid, "team.stage_changed", Map.of(
                "turnId", turn.getTurnId(),
                "stage", "COLLECTING"
        ));

        // Build context string from compressed memory
        String priorContext = buildPriorContext(ctx);

        // Parallel proposal collection
        Map<String, Future<TeamLlmResult>> futures = new LinkedHashMap<>();
        for (String modelId : modelIds) {
            futures.put(modelId, llmExecutor.submit(() -> withLlmPermit(() -> {
                String systemPrompt = "You are a team member. Provide your independent analysis "
                        + "and proposed solution. Be thorough and specific.";
                String userPrompt = turn.getUserMessage();
                if (priorContext != null && !priorContext.isBlank()) {
                    userPrompt = "Prior conversation context:\n" + priorContext
                            + "\n\nNew question:\n" + turn.getUserMessage();
                }
                return llmClient.complete(userUuid, modelId, systemPrompt, userPrompt, ctx.getKnowledgeBaseIds());
            })));
        }

        // Collect results with timeout and partial failure handling
        for (Map.Entry<String, Future<TeamLlmResult>> entry : futures.entrySet()) {
            String modelId = entry.getKey();
            try {
                TeamLlmResult result = entry.getValue().get(120, TimeUnit.SECONDS);
                MemberProposal proposal = new MemberProposal(modelId, result.content());
                turn.getProposals().put(modelId, proposal);
                turn.getProposalStatuses().put(modelId, "completed");
                sessionManager.saveTurn(turn);

                publishTurnEvent(turn.getTurnId(), userUuid, "team.member_proposal", Map.of(
                        "turnId", turn.getTurnId(),
                        "modelId", modelId,
                        "status", "completed",
                        "answerText", proposal.getAnswerText() != null ? proposal.getAnswerText() : ""
                ));
            } catch (TimeoutException e) {
                log.warn("[debate] model {} timed out during COLLECTING", modelId);
                turn.getFailedModels().add(modelId);
                turn.getProposalStatuses().put(modelId, "timeout");
                sessionManager.saveTurn(turn);
                entry.getValue().cancel(true);
                publishTurnEvent(turn.getTurnId(), userUuid, "team.member_proposal", Map.of(
                        "turnId", turn.getTurnId(),
                        "modelId", modelId,
                        "status", "timeout"
                ));
            } catch (Exception e) {
                log.warn("[debate] model {} failed during COLLECTING: {}", modelId, e.getMessage());
                turn.getFailedModels().add(modelId);
                turn.getProposalStatuses().put(modelId, "failed");
                sessionManager.saveTurn(turn);
                publishTurnEvent(turn.getTurnId(), userUuid, "team.member_proposal", Map.of(
                        "turnId", turn.getTurnId(),
                        "modelId", modelId,
                        "status", "failed"
                ));
            }
        }

        if (turn.getProposals().isEmpty()) {
            throw new IllegalStateException("All models failed during COLLECTING stage");
        }

        sessionManager.saveTurn(turn);
    }

    // ========== Stage 2: VOTING (Scheme C) ==========

    /**
     * Scheme C captain election: 1 judge initial ranking + 2 Position Flip verifications.
     * Fixed 3 LLM calls regardless of team size.
     */
    private void stageVoting(DebateTurnSession turn, TeamConversationContext ctx,
                             UUID userUuid) {
        turn.advanceTo(DebateStage.VOTING);
        sessionManager.saveTurn(turn);

        publishTurnEvent(turn.getTurnId(), userUuid, "team.stage_changed", Map.of(
                "turnId", turn.getTurnId(),
                "stage", "VOTING"
        ));

        if (turn.getCaptainSelectionMode() == CaptainSelectionMode.FIXED_FIRST) {
            // Skip voting entirely
            String firstModel = ctx.getSelectedModelIds().get(0);
            turn.setCaptainModelId(firstModel);
            turn.setCaptainSource(CaptainSource.FIXED_FIRST_MODEL);

            VotingRecord record = new VotingRecord();
            record.setElectedCaptainModelId(firstModel);
            record.setDecisionExplanation("\u56fa\u5b9a\u9009\u62e9\u9996\u4e2a\u6a21\u578b\u4f5c\u4e3a\u961f\u957f (fixed_first \u6a21\u5f0f)");
            record.setVerificationConsistent(true);
            turn.setVotingRecord(record);
            sessionManager.saveTurn(turn);
            publishTurnEvent(turn.getTurnId(), userUuid, "team.captain_elected", Map.of(
                    "turnId", turn.getTurnId(),
                    "captainModelId", firstModel,
                    "captainSource", CaptainSource.FIXED_FIRST_MODEL.name(),
                    "explanation", record.getDecisionExplanation()
            ));
            return;
        }

        // Auto election via Scheme C
        List<String> candidateIds = new ArrayList<>(turn.getProposals().keySet());
        if (candidateIds.size() == 1) {
            // Only one model produced a proposal
            turn.setCaptainModelId(candidateIds.get(0));
            turn.setCaptainSource(CaptainSource.AUTO_ELECTED);
            VotingRecord record = new VotingRecord();
            record.setElectedCaptainModelId(candidateIds.get(0));
            record.setDecisionExplanation("\u4ec5\u6709\u4e00\u4e2a\u5019\u9009\u6a21\u578b\uff0c\u9ed8\u8ba4\u5f53\u9009");
            record.setVerificationConsistent(true);
            turn.setVotingRecord(record);
            sessionManager.saveTurn(turn);
            publishTurnEvent(turn.getTurnId(), userUuid, "team.captain_elected", Map.of(
                    "turnId", turn.getTurnId(),
                    "captainModelId", candidateIds.get(0),
                    "captainSource", CaptainSource.AUTO_ELECTED.name(),
                    "explanation", record.getDecisionExplanation()
            ));
            return;
        }

        // Step 1: Pick a judge model (first available that is not a candidate, or first candidate)
        String judgeModel = pickJudgeModel(candidateIds, ctx.getSelectedModelIds());

        // Step 2: Build anonymized proposals
        Map<String, String> letterToModelId = new LinkedHashMap<>();
        StringBuilder anonymized = new StringBuilder();
        char letter = 'A';
        for (String modelId : candidateIds) {
            String label = String.valueOf(letter++);
            letterToModelId.put(label, modelId);
            anonymized.append("--- Proposal ").append(label).append(" ---\n");
            anonymized.append(turn.getProposals().get(modelId).getAnswerText()).append("\n\n");
        }

        // Step 3: Call 1 - Initial ranking
        VotingRecord votingRecord = new VotingRecord();
        votingRecord.setJudgeModelId(judgeModel);
        TeamChatRuntimeConfigService.Snapshot config = runtimeConfigService.snapshot();

        String rankingPrompt = TeamPrompts.schemeC_InitialRanking(
                turn.getUserMessage(), anonymized.toString());
        TeamLlmResult rankingResponse = completeWithTimeout(
                userUuid,
                judgeModel,
                "You are an impartial evaluator.",
                rankingPrompt,
                2048,
                0.1,
                ctx.getKnowledgeBaseIds(),
                config.votingTimeoutSeconds()
        );

        List<String> ranking = parseRanking(rankingResponse.content(), letterToModelId);
        votingRecord.setInitialRanking(ranking);

        if (ranking.size() < 2) {
            // Fallback: deterministic lexicographic sort
            ranking = new ArrayList<>(candidateIds);
            Collections.sort(ranking);
            votingRecord.setInitialRanking(ranking);
        }

        String firstPlace = ranking.get(0);
        String secondPlace = ranking.get(1);

        // Step 4: Call 2 - Position Flip verification [first, second]
        String firstProposal = turn.getProposals().get(firstPlace).getAnswerText();
        String secondProposal = turn.getProposals().get(secondPlace).getAnswerText();

        TeamLlmResult verifyAB = completeWithTimeout(
                userUuid,
                judgeModel,
                "You are an impartial evaluator.",
                TeamPrompts.schemeC_PositionFlipVerification(
                        turn.getUserMessage(), firstProposal, "A", secondProposal, "B"
                ),
                1024,
                0.1,
                ctx.getKnowledgeBaseIds(),
                config.votingTimeoutSeconds()
        );
        votingRecord.setVerificationResultAB(verifyAB.content());

        // Step 5: Call 3 - Position Flip verification [second, first] (flipped order)
        TeamLlmResult verifyBA = completeWithTimeout(
                userUuid,
                judgeModel,
                "You are an impartial evaluator.",
                TeamPrompts.schemeC_PositionFlipVerification(
                        turn.getUserMessage(), secondProposal, "A", firstProposal, "B"
                ),
                1024,
                0.1,
                ctx.getKnowledgeBaseIds(),
                config.votingTimeoutSeconds()
        );
        votingRecord.setVerificationResultBA(verifyBA.content());

        // Step 6: Resolve
        String winnerAB = parseWinner(verifyAB.content());
        String winnerBA = parseWinner(verifyBA.content());

        // Map position labels back: in AB test, A=firstPlace, B=secondPlace
        // In BA test, A=secondPlace, B=firstPlace
        String resolvedFromAB = "A".equals(winnerAB) ? firstPlace : secondPlace;
        String resolvedFromBA = "B".equals(winnerBA) ? firstPlace : secondPlace;

        boolean consistent = firstPlace.equals(resolvedFromAB) && firstPlace.equals(resolvedFromBA);
        votingRecord.setVerificationConsistent(consistent);

        String electedCaptain;
        String explanation;
        if (consistent) {
            electedCaptain = firstPlace;
            explanation = "\u521d\u59cb\u6392\u540d\u4e0e\u4e24\u6b21\u4f4d\u7f6e\u7ffb\u8f6c\u9a8c\u8bc1\u7ed3\u679c\u4e00\u81f4\uff1a"
                    + firstPlace + " \u63d0\u4f9b\u4e86\u6700\u4f73\u63d0\u6848\u3002";
        } else {
            // Conflict: use weighted scoring.
            // Initial ranking winner gets 2 points, each PF verification winner gets 1 point.
            Map<String, Integer> scores = new HashMap<>();
            scores.put(firstPlace, 2);
            scores.merge(resolvedFromAB, 1, Integer::sum);
            scores.merge(resolvedFromBA, 1, Integer::sum);

            electedCaptain = scores.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, Integer>, Integer>comparing(Map.Entry::getValue)
                            .thenComparing(Map.Entry::getKey))  // deterministic tiebreak: lexicographic
                    .map(Map.Entry::getKey)
                    .orElse(firstPlace);

            explanation = "\u9a8c\u8bc1\u7ed3\u679c\u4e0d\u4e00\u81f4\u3002\u5f97\u5206\uff1a" + scores
                    + "\u3002\u6700\u7ec8\u9009\u4e3e\uff1a" + electedCaptain + " (\u52a0\u6743\u5f97\u5206 + \u5b57\u5178\u5e8f\u51b3\u80dc)\u3002";
        }

        votingRecord.setElectedCaptainModelId(electedCaptain);
        votingRecord.setDecisionExplanation(explanation);

        turn.setCaptainModelId(electedCaptain);
        turn.setCaptainSource(CaptainSource.AUTO_ELECTED);
        turn.setVotingRecord(votingRecord);
        sessionManager.saveTurn(turn);

        publishTurnEvent(turn.getTurnId(), userUuid, "team.captain_elected", Map.of(
                "turnId", turn.getTurnId(),
                "captainModelId", electedCaptain,
                "captainSource", CaptainSource.AUTO_ELECTED.name(),
                "explanation", explanation
        ));
    }

    // ========== Stage 3: EXTRACTING ==========

    /**
     * Captain extracts divergence issues from all proposals.
     */
    private void stageExtracting(DebateTurnSession turn, TeamConversationContext ctx,
                                 UUID userUuid) {
        turn.advanceTo(DebateStage.EXTRACTING);
        sessionManager.saveTurn(turn);

        publishTurnEvent(turn.getTurnId(), userUuid, "team.stage_changed", Map.of(
                "turnId", turn.getTurnId(),
                "stage", "EXTRACTING",
                "captainModelId", turn.getCaptainModelId()
        ));

        // Build labeled proposals for captain
        StringBuilder allProposals = new StringBuilder();
        for (Map.Entry<String, MemberProposal> entry : turn.getProposals().entrySet()) {
            allProposals.append("--- ").append(entry.getKey()).append(" ---\n");
            allProposals.append(entry.getValue().getAnswerText()).append("\n\n");
        }

        String priorContext = buildPriorContext(ctx);
        String extractPrompt = TeamPrompts.extractIssues(
                turn.getUserMessage(), allProposals.toString(), priorContext);

        TeamChatRuntimeConfigService.Snapshot config = runtimeConfigService.snapshot();
        TeamLlmResult response = completeWithTimeout(
                userUuid,
                turn.getCaptainModelId(),
                "You are the team captain analyzing proposals.",
                extractPrompt,
                4096,
                0.2,
                ctx.getKnowledgeBaseIds(),
                config.extractionTimeoutSeconds()
        );

        List<DebateIssue> issues = parseIssues(response.content());
        turn.setIssues(issues);
        sessionManager.saveTurn(turn);

        publishTurnEvent(turn.getTurnId(), userUuid, "team.issues_extracted", Map.of(
                "turnId", turn.getTurnId(),
                "issueCount", issues.size(),
                "issues", issues.stream().map(i -> Map.of(
                        "issueId", i.getIssueId(),
                        "title", i.getTitle()
                )).toList()
        ));
    }

    // ========== Stage 4: DEBATING ==========

    /**
     * All models (including captain) debate each issue. Stance updates allowed.
     */
    private void stageDebating(DebateTurnSession turn, TeamConversationContext ctx,
                               UUID userUuid) {
        turn.advanceTo(DebateStage.DEBATING);
        sessionManager.saveTurn(turn);

        publishTurnEvent(turn.getTurnId(), userUuid, "team.stage_changed", Map.of(
                "turnId", turn.getTurnId(),
                "stage", "DEBATING"
        ));

        if (turn.getIssues().isEmpty()) {
            // No divergence: skip to synthesis
            log.info("[debate] No issues extracted, skipping DEBATING: turnId={}", turn.getTurnId());
            for (String modelId : turn.getDebateStatuses().keySet()) {
                turn.getDebateStatuses().put(modelId, "completed");
            }
            sessionManager.saveTurn(turn);
            return;
        }

        String priorContext = buildPriorContext(ctx);
        List<String> activeModelIds = new ArrayList<>(turn.getProposals().keySet());
        int maxRounds = runtimeConfigService.snapshot().maxDebateRounds();

        for (DebateIssue issue : turn.getIssues()) {
            for (int round = 1; round <= maxRounds; round++) {
                Map<String, Future<TeamLlmResult>> debateFutures = new LinkedHashMap<>();

                for (String modelId : activeModelIds) {
                    if ("failed".equalsIgnoreCase(turn.getDebateStatuses().get(modelId))) {
                        continue;
                    }
                    final int currentRound = round;
                    debateFutures.put(modelId, llmExecutor.submit(() -> withLlmPermit(() -> {
                        String otherStances = buildOtherStances(turn, issue.getIssueId(), modelId);
                        String modelProposal = turn.getProposals().get(modelId).getAnswerText();

                        return llmClient.complete(userUuid, modelId,
                                "You are a team member in a collaborative discussion.",
                                TeamPrompts.debateOnIssue(
                                        turn.getUserMessage(), issue.getTitle(),
                                        issue.getDescription(), otherStances,
                                        modelProposal, priorContext),
                                2048, 0.4, ctx.getKnowledgeBaseIds());
                    })));
                }

                if (debateFutures.isEmpty()) {
                    break;
                }

                List<DebateEntry> roundEntries = new ArrayList<>();
                for (Map.Entry<String, Future<TeamLlmResult>> entry : debateFutures.entrySet()) {
                    String modelId = entry.getKey();
                    try {
                        TeamLlmResult response = entry.getValue().get(90, TimeUnit.SECONDS);
                        DebateEntry debateEntry = parseDebateEntry(response.content(), modelId, issue.getIssueId(), round);
                        turn.getDebateEntries().add(debateEntry);
                        roundEntries.add(debateEntry);
                        turn.getDebateStatuses().put(modelId, "completed");
                        sessionManager.saveTurn(turn);

                        publishTurnEvent(turn.getTurnId(), userUuid, "team.debate_entry", Map.of(
                                "turnId", turn.getTurnId(),
                                "modelId", modelId,
                                "issueId", issue.getIssueId(),
                                "round", round,
                                "stanceChanged", debateEntry.isStanceChanged(),
                                "argument", debateEntry.getArgument() != null ? debateEntry.getArgument() : "",
                                "stance", debateEntry.getStance() != null ? debateEntry.getStance() : ""
                        ));
                    } catch (Exception e) {
                        log.warn("[debate] model {} failed on issue {} round {}: {}",
                                modelId, issue.getIssueId(), round, e.getMessage());
                        turn.getDebateStatuses().put(modelId, "failed");
                        turn.getFailedModels().add(modelId);
                        sessionManager.saveTurn(turn);
                    }
                }
                if (round >= 2 && issueConverged(turn, issue.getIssueId(), round, roundEntries)) {
                    log.info("[debate] issue {} converged at round {}", issue.getIssueId(), round);
                    break;
                }
            }
        }

        sessionManager.saveTurn(turn);
    }

    // ========== Stage 5: SYNTHESIZING ==========

    /**
     * Captain synthesizes all proposals and debate outcomes into a final answer.
     */
    private void stageSynthesizing(DebateTurnSession turn, TeamConversationContext ctx,
                                   UUID userUuid) {
        turn.advanceTo(DebateStage.SYNTHESIZING);
        sessionManager.saveTurn(turn);

        publishTurnEvent(turn.getTurnId(), userUuid, "team.stage_changed", Map.of(
                "turnId", turn.getTurnId(),
                "stage", "SYNTHESIZING",
                "captainModelId", turn.getCaptainModelId()
        ));

        // Build debate summary
        StringBuilder debateSummary = new StringBuilder();
        for (DebateEntry entry : turn.getDebateEntries()) {
            debateSummary.append("[").append(entry.getModelId()).append(" on ")
                    .append(entry.getIssueId()).append("]: ")
                    .append(entry.getStance() != null ? entry.getStance() : entry.getArgument())
                    .append(entry.isStanceChanged() ? " (stance updated)" : "")
                    .append("\n");
        }

        StringBuilder allProposals = new StringBuilder();
        for (Map.Entry<String, MemberProposal> entry : turn.getProposals().entrySet()) {
            allProposals.append("--- ").append(entry.getKey()).append(" ---\n");
            allProposals.append(entry.getValue().getAnswerText()).append("\n\n");
        }

        String priorContext = buildPriorContext(ctx);
        String synthesizePrompt = TeamPrompts.synthesize(
                turn.getUserMessage(), allProposals.toString(),
                debateSummary.toString(), priorContext);

        StringBuilder answerBuilder = new StringBuilder();
        llmClient.completeStreaming(
                userUuid,
                turn.getCaptainModelId(),
                "You are the team captain delivering the final answer.",
                synthesizePrompt,
                8192,
                0.3,
                ctx.getKnowledgeBaseIds(),
                delta -> {
                    answerBuilder.append(delta);
                    publishTurnEvent(turn.getTurnId(), userUuid, "team.final_answer_delta", Map.of(
                            "conversationId", turn.getConversationId(),
                            "turnId", turn.getTurnId(),
                            "delta", delta
                    ));
                    if (crossedFlushThreshold(answerBuilder.length(), delta.length())) {
                        turn.setFinalAnswer(answerBuilder.toString());
                        sessionManager.saveTurn(turn);
                    }
                }
        );

        turn.setFinalAnswer(answerBuilder.toString());
        sessionManager.saveTurn(turn);
        publishTurnEvent(turn.getTurnId(), userUuid, "team.final_answer_done", Map.of(
                "conversationId", turn.getConversationId(),
                "turnId", turn.getTurnId()
        ));
    }

    // ========== Turn Finalization ==========

    /**
     * Persist the minimum completed turn state required for client recovery before
     * any async memory compression starts.
     */
    private void persistCompletedTurn(DebateTurnSession turn, TeamConversationContext ctx) {
        turn.advanceTo(DebateStage.COMPLETED);
        sessionManager.saveTurn(turn);

        ctx.setLastActiveAt(Instant.now());
        ctx.setCompletedTurns(ctx.getCompletedTurns() + 1);
        ctx.setActiveTurnId(turn.getTurnId());
        ctx.getCaptainHistory().add(turn.getCaptainModelId());

        TeamConversationContext.DecisionRecord record = new TeamConversationContext.DecisionRecord();
        record.setTurnId(turn.getTurnId());
        record.setTurnNumber(turn.getTurnNumber());
        record.setCaptainModelId(turn.getCaptainModelId());
        record.setCaptainSource(turn.getCaptainSource());
        record.setUserQuestion(truncate(turn.getUserMessage(), 200));
        record.setFinalAnswerSummary(truncate(turn.getFinalAnswer(), 500));
        record.setKeyIssues(turn.getIssues().stream().map(DebateIssue::getTitle).toList());
        record.setTimestamp(Instant.now());
        ctx.getDecisionHistory().add(record);

        if (ctx.getDecisionHistory().size() > 20) {
            ctx.setDecisionHistory(new ArrayList<>(
                    ctx.getDecisionHistory().subList(
                            ctx.getDecisionHistory().size() - 20,
                            ctx.getDecisionHistory().size())));
        }

        sessionManager.saveConversation(ctx);
        try {
            persistenceService.persistCompletedTurn(ctx, turn);
        } catch (Exception e) {
            log.error("[debate] failed to persist completed turn to database: conversationId={}, turnId={}, error={}",
                    turn.getConversationId(), turn.getTurnId(), e.getMessage(), e);
        }
    }

    private void finalizeTurnAsync(String conversationId, String turnId, String userId) {
        try {
            TeamConversationContext ctx = sessionManager.loadConversation(conversationId, userId);
            DebateTurnSession turn = sessionManager.loadTurn(turnId, userId);
            finalizeTurn(turn, ctx);
        } catch (Exception e) {
            log.warn("[debate] async finalize failed: conversationId={}, turnId={}, error={}",
                    conversationId, turnId, e.getMessage());
        }
    }

    /**
     * After a successful turn, compress results into long-term TeamConversationContext.
     */
    private void finalizeTurn(DebateTurnSession turn, TeamConversationContext ctx) {
        TeamChatRuntimeConfigService.Snapshot config = runtimeConfigService.snapshot();
        // Compress memory using captain model
        String debateSummary = buildDebateSummaryText(turn);
        String compressPrompt = TeamPrompts.compressMemory(
                turn.getUserMessage(), turn.getFinalAnswer(),
                debateSummary, ctx.getSharedSummary());

        String compressed;
        try {
            compressed = llmClient.complete(UUID.fromString(turn.getUserId()), turn.getCaptainModelId(),
                    "You are summarizing a team conversation turn.", compressPrompt, 2048, 0.2, ctx.getKnowledgeBaseIds()).content();
        } catch (Exception e) {
            log.warn("[debate] memory compression failed, using fallback: {}", e.getMessage());
            compressed = "Q: " + truncate(turn.getUserMessage(), 200)
                    + "\nA: " + truncate(turn.getFinalAnswer(), 500);
        }
        compressed = truncate(compressed, config.sharedSummaryMaxChars());

        // Update long-term context
        ctx.setSharedSummary(compressed);
        ctx.setOpenIssues(turn.getIssues().stream()
                .filter(issue -> !issue.isResolved())
                .map(DebateIssue::getTitle)
                .filter(Objects::nonNull)
                .limit(10)
                .toList());
        ctx.setLastActiveAt(Instant.now());
        ctx.setMemoryVersion(ctx.getMemoryVersion() + 1);
        ctx.setActiveTurnId(turn.getTurnId());

        // Update per-member memory (compressed, excludes reasoning)
        for (Map.Entry<String, MemberProposal> entry : turn.getProposals().entrySet()) {
            String prev = ctx.getMemberMemories().getOrDefault(entry.getKey(), "");
            String memberSummary = truncate(entry.getValue().getAnswerText(), 300);
            ctx.getMemberMemories().put(entry.getKey(),
                    truncate(
                            prev + "\n[Turn " + turn.getTurnNumber() + "] " + memberSummary,
                            config.memberMemoryMaxChars()
                    ));
        }

        // Captain handoff summary
        turn.setCaptainHandoffSummary("Captain: " + turn.getCaptainModelId()
                + " (" + turn.getCaptainSource() + "). "
                + "Issues: " + turn.getIssues().size()
                + ". Final answer delivered.");
        ctx.setCaptainHandoffSummary(turn.getCaptainHandoffSummary());
        sessionManager.saveTurn(turn);

        sessionManager.saveConversation(ctx);
    }

    private List<Map<String, Object>> buildMemberStatuses(DebateTurnSession turn) {
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        modelIds.addAll(turn.getProposalStatuses().keySet());
        modelIds.addAll(turn.getDebateStatuses().keySet());
        modelIds.addAll(turn.getProposals().keySet());
        modelIds.addAll(turn.getFailedModels());
        List<Map<String, Object>> memberStatuses = new ArrayList<>();
        for (String modelId : modelIds) {
            memberStatuses.add(Map.of(
                    "modelId", modelId,
                    "proposalStatus", turn.getProposalStatuses().getOrDefault(modelId, "pending"),
                    "debateStatus", turn.getDebateStatuses().getOrDefault(modelId, "pending")
            ));
        }
        return memberStatuses;
    }

    private TeamConversationContext loadConversationOrRestore(String conversationId, String userId) {
        try {
            return sessionManager.loadConversation(conversationId, userId);
        } catch (IllegalStateException error) {
            if (!isMissingConversation(error)) {
                throw error;
            }
            return persistenceService.restoreConversationContext(conversationId, userId)
                    .orElseThrow(() -> error);
        }
    }

    private boolean isMissingConversation(IllegalStateException error) {
        String message = error.getMessage();
        return message != null && message.startsWith("Team conversation not found or expired:");
    }

    private String buildConversationTitle(String userMessage) {
        return truncate(userMessage, 50);
    }

    // ========== Helper Methods ==========

    private String buildPriorContext(TeamConversationContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.getSharedSummary() != null && !ctx.getSharedSummary().isBlank()) {
            sb.append("Shared summary from prior turns:\n")
                    .append(ctx.getSharedSummary());
        }

        if (ctx.getCaptainHandoffSummary() != null && !ctx.getCaptainHandoffSummary().isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("Latest captain handoff:\n")
                    .append(ctx.getCaptainHandoffSummary());
        }

        if (ctx.getMemberMemories() != null && !ctx.getMemberMemories().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("Member memory snapshots:\n");
            for (Map.Entry<String, String> entry : ctx.getMemberMemories().entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                sb.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(truncate(entry.getValue(), 400))
                        .append("\n");
            }
        }

        if (ctx.getOpenIssues() != null && !ctx.getOpenIssues().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("Unresolved issues from prior turns:\n");
            for (String issue : ctx.getOpenIssues()) {
                sb.append("- ").append(issue).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildOtherStances(DebateTurnSession turn, String issueId, String excludeModelId) {
        StringBuilder sb = new StringBuilder();
        for (DebateEntry entry : turn.getDebateEntries()) {
            if (entry.getIssueId().equals(issueId) && !entry.getModelId().equals(excludeModelId)) {
                sb.append("[").append(entry.getModelId()).append("]: ");
                sb.append(entry.getStance() != null ? entry.getStance() : entry.getArgument());
                sb.append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "No other positions yet.";
    }

    private String buildDebateSummaryText(DebateTurnSession turn) {
        StringBuilder sb = new StringBuilder();
        for (DebateIssue issue : turn.getIssues()) {
            sb.append("Issue: ").append(issue.getTitle()).append("\n");
            for (DebateEntry entry : turn.getDebateEntries()) {
                if (entry.getIssueId().equals(issue.getIssueId())) {
                    sb.append("  [").append(entry.getModelId()).append("]: ")
                            .append(entry.getStance() != null ? entry.getStance() : "")
                            .append(entry.isStanceChanged() ? " (updated)" : "")
                            .append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Pick a judge model for Scheme C. Prefers a model not in the candidate list
     * to avoid self-judging bias, but falls back to the first candidate if needed.
     */
    private String pickJudgeModel(List<String> candidateIds, List<String> allModelIds) {
        // Try to find a model not in the candidate list
        for (String modelId : allModelIds) {
            if (!candidateIds.contains(modelId)) {
                return modelId;
            }
        }
        // Fallback: use first candidate (still anonymous, just can't avoid self-eval)
        return candidateIds.get(0);
    }

    // ========== JSON Parsing Utilities ==========

    private List<String> parseRanking(String response, Map<String, String> letterToModelId) {
        try {
            String json = extractJsonFromResponse(response);
            JsonNode root = objectMapper.readTree(json);
            JsonNode rankingNode = root.path("ranking");
            if (rankingNode.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode letterNode : rankingNode) {
                    String letter = letterNode.asText("").trim().toUpperCase();
                    String modelId = letterToModelId.get(letter);
                    if (modelId != null) {
                        result.add(modelId);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("[debate] failed to parse ranking response: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private String parseWinner(String response) {
        try {
            String json = extractJsonFromResponse(response);
            JsonNode root = objectMapper.readTree(json);
            return root.path("winner").asText("A").trim().toUpperCase();
        } catch (Exception e) {
            log.warn("[debate] failed to parse winner response: {}", e.getMessage());
            return "A";
        }
    }

    private List<DebateIssue> parseIssues(String response) {
        try {
            String json = extractJsonFromResponse(response);
            List<DebateIssue> issues = objectMapper.readValue(json, new TypeReference<>() {});
            // Ensure IDs
            for (int i = 0; i < issues.size(); i++) {
                if (issues.get(i).getIssueId() == null || issues.get(i).getIssueId().isBlank()) {
                    issues.get(i).setIssueId("issue-" + (i + 1));
                }
            }
            return issues;
        } catch (Exception e) {
            log.warn("[debate] failed to parse issues, fallback to empty: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private DebateEntry parseDebateEntry(String response, String modelId, String issueId, int round) {
        DebateEntry entry = new DebateEntry();
        entry.setModelId(modelId);
        entry.setIssueId(issueId);
        entry.setRound(round);

        try {
            String json = extractJsonFromResponse(response);
            JsonNode root = objectMapper.readTree(json);
            entry.setStance(root.path("stance").asText(""));
            entry.setArgument(root.path("argument").asText(""));
            entry.setStanceChanged(root.path("stanceChanged").asBoolean(false));
        } catch (Exception e) {
            // Fallback: treat entire response as the argument
            entry.setStance("");
            entry.setArgument(response);
            entry.setStanceChanged(false);
        }
        return entry;
    }

    /**
     * Extract JSON content from a response that may contain markdown code fences.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();

        // Strip markdown code fences
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private TeamLlmResult completeWithTimeout(
            UUID userId,
            String model,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            double temperature,
            List<String> knowledgeBaseIds,
            int timeoutSeconds
    ) {
        Future<TeamLlmResult> future = llmExecutor.submit(() ->
                withLlmPermit(() -> llmClient.complete(userId, model, systemPrompt, userPrompt, maxTokens, temperature, knowledgeBaseIds)));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("team llm timeout");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("team llm interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("team llm failed", cause);
        }
    }

    private void applyVotingFallback(DebateTurnSession turn, TeamConversationContext ctx, UUID userUuid) {
        String fallback = ctx.getSelectedModelIds().get(0);
        turn.setCaptainModelId(fallback);
        turn.setCaptainSource(CaptainSource.FIXED_FIRST_MODEL);

        VotingRecord record = new VotingRecord();
        record.setElectedCaptainModelId(fallback);
        record.setDecisionExplanation("\u9009\u4e3e\u5931\u8d25\uff1b\u56fa\u5b9a\u9009\u62e9\u9996\u4e2a\u6a21\u578b\u4f5c\u4e3a\u961f\u957f\u3002");
        record.setVerificationConsistent(false);
        turn.setVotingRecord(record);
        sessionManager.saveTurn(turn);

        publishTurnEvent(turn.getTurnId(), userUuid, "team.captain_elected", Map.of(
                "turnId", turn.getTurnId(),
                "captainModelId", fallback,
                "captainSource", CaptainSource.FIXED_FIRST_MODEL.name(),
                "explanation", record.getDecisionExplanation()
        ));
    }

    private boolean crossedFlushThreshold(int totalLength, int deltaLength) {
        int previousLength = Math.max(0, totalLength - deltaLength);
        return (previousLength / FINAL_ANSWER_FLUSH_CHARS) < (totalLength / FINAL_ANSWER_FLUSH_CHARS);
    }

    private boolean issueConverged(DebateTurnSession turn, String issueId, int round, List<DebateEntry> currentRoundEntries) {
        if (round < 2 || currentRoundEntries.isEmpty()) {
            return false;
        }
        boolean anyStanceChanged = currentRoundEntries.stream().anyMatch(DebateEntry::isStanceChanged);
        if (anyStanceChanged) {
            return false;
        }
        Map<String, String> previous = new HashMap<>();
        Map<String, String> current = new HashMap<>();
        for (DebateEntry entry : turn.getDebateEntries()) {
            if (!issueId.equals(entry.getIssueId())) {
                continue;
            }
            String normalized = normalizeDebateText(entry);
            if (entry.getRound() == round - 1) {
                previous.put(entry.getModelId(), normalized);
            } else if (entry.getRound() == round) {
                current.put(entry.getModelId(), normalized);
            }
        }
        if (previous.isEmpty() || current.isEmpty()) {
            return false;
        }
        for (DebateEntry entry : currentRoundEntries) {
            String modelId = entry.getModelId();
            if (!Objects.equals(previous.get(modelId), current.get(modelId))) {
                return false;
            }
        }
        return true;
    }

    private String normalizeDebateText(DebateEntry entry) {
        String source = entry.getStance() != null && !entry.getStance().isBlank()
                ? entry.getStance()
                : entry.getArgument();
        return source == null ? "" : source.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private <T> T withLlmPermit(Callable<T> callable) throws Exception {
        syncLlmConcurrency();
        llmConcurrency.acquire();
        try {
            return callable.call();
        } finally {
            llmConcurrency.release();
        }
    }

    private void syncLlmConcurrency() {
        int configured = runtimeConfigService.snapshot().maxLlmConcurrency();
        synchronized (llmConcurrency) {
            if (configured == llmConcurrencyLimit) {
                return;
            }
            if (configured > llmConcurrencyLimit) {
                llmConcurrency.release(configured - llmConcurrencyLimit);
            } else {
                llmConcurrency.reduce(llmConcurrencyLimit - configured);
            }
            llmConcurrencyLimit = configured;
        }
    }

    private void publishTurnEvent(String turnId, UUID userUuid, String event, Map<String, Object> payload) {
        sessionManager.appendTurnEvent(turnId, event, payload);
        ws.sendToUser(userUuid, event, payload);
    }

    private List<String> copyKnowledgeBaseIds(List<String> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(knowledgeBaseIds));
    }

    private static ExecutorService newNamedExecutor(String prefix) {
        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + threadCounter.getAndIncrement());
            return thread;
        };
        return Executors.newCachedThreadPool(threadFactory);
    }

    private static final class AdjustableSemaphore extends Semaphore {
        private AdjustableSemaphore(int permits) {
            super(permits, true);
        }

        private void reduce(int reduction) {
            super.reducePermits(reduction);
        }
    }
}
