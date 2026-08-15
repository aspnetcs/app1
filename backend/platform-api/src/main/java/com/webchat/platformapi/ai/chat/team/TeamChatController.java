package com.webchat.platformapi.ai.chat.team;

import com.webchat.platformapi.ai.chat.team.dto.*;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for multi-turn team debate conversations.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>POST   /api/v1/team-chat/start      - Start a new team conversation</li>
 *   <li>POST   /api/v1/team-chat/continue    - Send a follow-up question</li>
 *   <li>GET    /api/v1/team-chat/status       - Query current turn status</li>
 *   <li>GET    /api/v1/team-chat/history       - Read completed turns history</li>
 *   <li>GET    /api/v1/team-chat/events       - Incremental event polling (non-SSE platforms)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/team-chat")
public class TeamChatController {

    private static final Logger log = LoggerFactory.getLogger(TeamChatController.class);

    private final DebateConsensusService debateService;
    private final DebateSessionManager sessionManager;
    private final TeamChatRuntimeConfigService runtimeConfigService;
    private final RolePolicyService rolePolicyService;
    private final TeamConversationPersistenceService persistenceService;

    public TeamChatController(
            DebateConsensusService debateService,
            DebateSessionManager sessionManager,
            TeamChatRuntimeConfigService runtimeConfigService,
            RolePolicyService rolePolicyService,
            TeamConversationPersistenceService persistenceService
    ) {
        this.debateService = debateService;
        this.sessionManager = sessionManager;
        this.runtimeConfigService = runtimeConfigService;
        this.rolePolicyService = rolePolicyService;
        this.persistenceService = persistenceService;
    }

    // ========== Start ==========

    /**
     * Start a new team conversation with a first question.
     * Returns conversationId immediately; five-stage consensus runs asynchronously.
     */
    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        TeamChatRuntimeConfigService.Snapshot config = runtimeConfigService.snapshot();
        if (!config.enabled()) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "team chat is disabled");
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "request body required");

        String message = str(body.get("message"));
        if (message == null || message.isBlank()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "message is required");
        }

        @SuppressWarnings("unchecked")
        List<String> modelIds = body.get("modelIds") instanceof List<?> list
                ? list.stream()
                .map(TeamChatController::str)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                : List.of();
        if (modelIds.size() < 2) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "at least 2 models required");
        }
        if (new LinkedHashSet<>(modelIds).size() != modelIds.size()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "duplicate models are not allowed");
        }
        if (modelIds.size() > config.maxModels()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "max " + config.maxModels() + " models allowed");
        }
        ApiResponse<Map<String, Object>> modelPolicyError = validateAllowedModels(userId, role, modelIds);
        if (modelPolicyError != null) {
            return modelPolicyError;
        }

        String modeStr = str(body.get("captainSelectionMode"));
        CaptainSelectionMode mode = parseCaptainMode(modeStr);
        List<String> knowledgeBaseIds = normalizeStringList(body.get("knowledgeBaseIds"));

        try {
            DebateConsensusService.TurnLaunchResult launch = debateService.startConversation(
                    userId.toString(), modelIds, mode, knowledgeBaseIds, message);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("conversationId", launch.conversationId());
            response.put("turnId", launch.turnId());
            response.put("turnNumber", launch.turnNumber());
            response.put("captainSelectionMode", mode.name().toLowerCase());
            response.put("models", modelIds);
            response.put("transportMode", "websocket");
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("[team-chat] start failed: userId={}, error={}", userId, e.getMessage(), e);
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "failed to start team conversation");
        }
    }

    // ========== Continue ==========

    /**
     * Continue an existing team conversation with a follow-up question.
     */
    @PostMapping("/continue")
    public ApiResponse<Map<String, Object>> continueChat(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!runtimeConfigService.snapshot().enabled()) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "team chat is disabled");
        if (body == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "request body required");

        String conversationId = str(body.get("conversationId"));
        String message = str(body.get("message"));
        List<String> knowledgeBaseIds = body.containsKey("knowledgeBaseIds")
                ? normalizeStringList(body.get("knowledgeBaseIds"))
                : null;
        if (conversationId == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "conversationId is required");
        if (message == null || message.isBlank()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "message is required");

        try {
            DebateConsensusService.TurnLaunchResult launch =
                    debateService.continueConversation(conversationId, userId.toString(), knowledgeBaseIds, message);
            return ApiResponse.ok(Map.of(
                    "conversationId", launch.conversationId(),
                    "turnId", launch.turnId(),
                    "turnNumber", launch.turnNumber(),
                    "transportMode", "websocket"
            ));
        } catch (SecurityException e) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("[team-chat] continue failed: userId={}, convId={}, error={}",
                    userId, conversationId, e.getMessage(), e);
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "failed to continue team conversation");
        }
    }

    // ========== Status ==========

    /**
     * Query the current status of an active team conversation turn.
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam String conversationId,
            @RequestParam(required = false) String turnId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!runtimeConfigService.snapshot().enabled()) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "team chat is disabled");

        try {
            TeamConversationContext ctx = sessionManager.loadConversation(conversationId, userId.toString());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("conversationId", conversationId);
            response.put("completedTurns", ctx.getCompletedTurns());
            response.put("captainSelectionMode", ctx.getCaptainSelectionMode().name().toLowerCase());
            response.put("memoryVersion", ctx.getMemoryVersion());
            response.put("sharedSummary", ctx.getSharedSummary());
            response.put("captainHistory", ctx.getCaptainHistory());
            response.put("models", ctx.getSelectedModelIds());
            response.put("activeTurnId", RequestUtils.trimOrNull(ctx.getActiveTurnId()));

            String resolvedTurnId = RequestUtils.trimOrNull(turnId);
            if (resolvedTurnId == null) {
                resolvedTurnId = RequestUtils.trimOrNull(ctx.getActiveTurnId());
            }

            if (resolvedTurnId != null) {
                try {
                    DebateTurnSession turn = sessionManager.loadTurn(resolvedTurnId, userId.toString());
                    response.put("turn", buildTurnStatus(turn));
                } catch (IllegalStateException e) {
                    String activeTurnId = RequestUtils.trimOrNull(ctx.getActiveTurnId());
                    if (resolvedTurnId.equals(activeTurnId)) {
                        ctx.setActiveTurnId(null);
                        sessionManager.saveConversation(ctx);
                        response.put("activeTurnId", null);
                    }
                    response.put("turn", Map.of("status", "expired_or_not_found"));
                }
            }

            return ApiResponse.ok(response);
        } catch (SecurityException e) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, e.getMessage());
        } catch (IllegalStateException e) {
            if (isMissingConversation(e)) {
                Optional<TeamConversationPersistenceService.PersistedTeamConversationSnapshot> snapshot =
                        persistenceService.loadPersistedConversation(conversationId, userId.toString());
                if (snapshot.isPresent()) {
                    return ApiResponse.ok(buildPersistedStatusResponse(conversationId, snapshot.orElseThrow()));
                }
            }
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    // ========== History ==========

    /**
     * Read completed turns history for UI recovery after page refresh.
     */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam String conversationId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!runtimeConfigService.snapshot().enabled()) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "team chat is disabled");

        try {
            TeamConversationContext ctx = sessionManager.loadConversation(conversationId, userId.toString());

            List<Map<String, Object>> turns = new ArrayList<>();
            for (TeamConversationContext.DecisionRecord record : ctx.getDecisionHistory()) {
                Map<String, Object> turn = new LinkedHashMap<>();
                turn.put("turnId", record.getTurnId());
                turn.put("turnNumber", record.getTurnNumber());
                turn.put("captainModelId", record.getCaptainModelId());
                turn.put("captainSource", record.getCaptainSource() != null ? record.getCaptainSource().name().toLowerCase() : null);
                turn.put("userQuestion", record.getUserQuestion());
                turn.put("finalAnswerSummary", record.getFinalAnswerSummary());
                turn.put("keyIssues", record.getKeyIssues());
                turn.put("timestamp", record.getTimestamp() != null ? record.getTimestamp().toString() : null);
                turns.add(turn);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("conversationId", conversationId);
            response.put("models", ctx.getSelectedModelIds());
            response.put("captainSelectionMode", ctx.getCaptainSelectionMode().name().toLowerCase());
            response.put("completedTurns", ctx.getCompletedTurns());
            response.put("decisionHistory", turns);
            response.put("sharedSummary", ctx.getSharedSummary());
            response.put("memoryVersion", ctx.getMemoryVersion());
            response.put("activeTurnId", RequestUtils.trimOrNull(ctx.getActiveTurnId()));
            return ApiResponse.ok(response);
        } catch (SecurityException e) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, e.getMessage());
        } catch (IllegalStateException e) {
            if (isMissingConversation(e)) {
                Optional<TeamConversationPersistenceService.PersistedTeamConversationSnapshot> snapshot =
                        persistenceService.loadPersistedConversation(conversationId, userId.toString());
                if (snapshot.isPresent()) {
                    return ApiResponse.ok(buildPersistedHistoryResponse(conversationId, snapshot.orElseThrow()));
                }
            }
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    // ========== Events ==========

    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> events(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam String conversationId,
            @RequestParam(required = false) String turnId,
            @RequestParam(defaultValue = "0") long cursor
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!runtimeConfigService.snapshot().enabled()) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "team chat is disabled");

        try {
            TeamConversationContext ctx = sessionManager.loadConversation(conversationId, userId.toString());
            String resolvedTurnId = RequestUtils.trimOrNull(turnId);
            long safeCursor = Math.max(0, cursor);
            if (resolvedTurnId == null) {
                resolvedTurnId = RequestUtils.trimOrNull(ctx.getActiveTurnId());
            }
            if (resolvedTurnId == null) {
                return ApiResponse.ok(buildEventsPage(
                        conversationId,
                        null,
                        safeCursor,
                        safeCursor,
                        false,
                        "IDLE",
                        List.of()
                ));
            }

            try {
                DebateTurnSession turn = sessionManager.loadTurn(resolvedTurnId, userId.toString());
                if (!conversationId.equals(turn.getConversationId())) {
                    return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "turn does not belong to conversation");
                }
                DebateSessionManager.TurnEventPage page = sessionManager.readTurnEvents(
                        resolvedTurnId,
                        userId.toString(),
                        cursor,
                        200
                );

                return ApiResponse.ok(buildEventsPage(
                        conversationId,
                        resolvedTurnId,
                        safeCursor,
                        page.nextCursor(),
                        page.completed(),
                        page.stage(),
                        page.events()
                ));
            } catch (IllegalStateException e) {
                if (!isMissingTurn(e)) {
                    throw e;
                }
                String activeTurnId = RequestUtils.trimOrNull(ctx.getActiveTurnId());
                if (resolvedTurnId.equals(activeTurnId)) {
                    ctx.setActiveTurnId(null);
                    sessionManager.saveConversation(ctx);
                }
                return ApiResponse.ok(buildEventsPage(
                        conversationId,
                        resolvedTurnId,
                        safeCursor,
                        safeCursor,
                        true,
                        "expired_or_not_found",
                        List.of()
                ));
            }
        } catch (SecurityException e) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, e.getMessage());
        } catch (IllegalStateException e) {
            if (isMissingConversation(e)) {
                Optional<TeamConversationPersistenceService.PersistedTeamConversationSnapshot> snapshot =
                        persistenceService.loadPersistedConversation(conversationId, userId.toString());
                if (snapshot.isPresent()) {
                    return ApiResponse.ok(buildPersistedEventsResponse(
                            conversationId,
                            turnId,
                            Math.max(0, cursor),
                            snapshot.orElseThrow()
                    ));
                }
            }
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, e.getMessage());
        }
    }

    // ========== Helper Methods ==========

    private Map<String, Object> buildTurnStatus(DebateTurnSession turn) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("turnId", turn.getTurnId());
        status.put("turnNumber", turn.getTurnNumber());
        status.put("stage", turn.getStage().name());
        status.put("captainModelId", turn.getCaptainModelId());
        status.put("captainSource", turn.getCaptainSource() != null ? turn.getCaptainSource().name().toLowerCase() : null);
        status.put("captainExplanation",
                turn.getVotingRecord() != null ? turn.getVotingRecord().getDecisionExplanation() : null);
        status.put("userMessage", turn.getUserMessage());
        status.put("issueCount", turn.getIssues().size());
        status.put("issues", turn.getIssues().stream().map(i -> Map.of(
                "issueId", i.getIssueId(),
                "title", i.getTitle(),
                "resolved", i.isResolved()
        )).toList());
        List<Map<String, Object>> memberStatuses = new ArrayList<>();
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        modelIds.addAll(turn.getProposalStatuses().keySet());
        modelIds.addAll(turn.getDebateStatuses().keySet());
        modelIds.addAll(turn.getProposals().keySet());
        modelIds.addAll(turn.getFailedModels());

        // Build a per-model debate-arguments index for the status response
        Map<String, List<Map<String, Object>>> debateArgsByModel = new HashMap<>();
        for (DebateEntry entry : turn.getDebateEntries()) {
            debateArgsByModel
                    .computeIfAbsent(entry.getModelId(), k -> new ArrayList<>())
                    .add(Map.of(
                            "issueId", entry.getIssueId() != null ? entry.getIssueId() : "",
                            "argument", entry.getArgument() != null ? entry.getArgument() : "",
                            "stance", entry.getStance() != null ? entry.getStance() : ""
                    ));
        }

        for (String modelId : modelIds) {
            MemberProposal proposal = turn.getProposals().get(modelId);
            Map<String, Object> memberEntry = new LinkedHashMap<>();
            memberEntry.put("modelId", modelId);
            memberEntry.put("proposalStatus", turn.getProposalStatuses().getOrDefault(modelId, "pending"));
            memberEntry.put("debateStatus", turn.getDebateStatuses().getOrDefault(modelId, "pending"));
            memberEntry.put("summary", proposal != null && proposal.getAnswerText() != null ? proposal.getAnswerText() : null);
            memberEntry.put("debateArguments", debateArgsByModel.getOrDefault(modelId, List.of()));
            memberStatuses.add(memberEntry);
        }
        status.put("memberStatuses", memberStatuses);
        status.put("finalAnswer", turn.getFinalAnswer());
        status.put("errorMessage", turn.getErrorMessage());
        status.put("failedModels", turn.getFailedModels());
        status.put("stageTimestamps", turn.getStageTimestamps().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toString()
                )));
        return status;
    }

    private Map<String, Object> buildEventsPage(
            String conversationId,
            String turnId,
            long cursor,
            long nextCursor,
            boolean completed,
            String stage,
            List<?> events
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversationId", conversationId);
        response.put("turnId", turnId);
        response.put("cursor", cursor);
        response.put("nextCursor", nextCursor);
        response.put("completed", completed);
        response.put("stage", stage);
        response.put("events", events);
        return response;
    }

    private Map<String, Object> buildPersistedStatusResponse(
            String conversationId,
            TeamConversationPersistenceService.PersistedTeamConversationSnapshot snapshot
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversationId", conversationId);
        response.put("completedTurns", snapshot.completedTurns());
        response.put("captainSelectionMode", snapshot.captainSelectionMode().name().toLowerCase());
        response.put("memoryVersion", snapshot.memoryVersion());
        response.put("sharedSummary", snapshot.sharedSummary());
        response.put("captainHistory", snapshot.captainHistory());
        response.put("models", snapshot.modelIds());
        response.put("activeTurnId", null);
        if (!snapshot.turns().isEmpty()) {
            response.put("turn", buildPersistedTurnStatus(snapshot.turns().get(snapshot.turns().size() - 1), snapshot.modelIds()));
        }
        return response;
    }

    private Map<String, Object> buildPersistedHistoryResponse(
            String conversationId,
            TeamConversationPersistenceService.PersistedTeamConversationSnapshot snapshot
    ) {
        List<Map<String, Object>> turns = new ArrayList<>();
        for (TeamConversationPersistenceService.PersistedTeamTurnSnapshot turn : snapshot.turns()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("turnId", turn.turnId());
            record.put("turnNumber", turn.turnNumber());
            record.put("captainModelId", turn.captainModelId());
            record.put("captainSource", turn.captainSource() != null ? turn.captainSource().name().toLowerCase() : null);
            record.put("userQuestion", turn.userQuestion());
            record.put("finalAnswerSummary", truncate(turn.finalAnswer(), 500));
            record.put("keyIssues", turn.issues().stream()
                    .map(TeamConversationPersistenceService.PersistedTeamIssue::title)
                    .toList());
            record.put("timestamp", turn.timestamp() != null ? turn.timestamp().toString() : null);
            turns.add(record);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversationId", conversationId);
        response.put("models", snapshot.modelIds());
        response.put("captainSelectionMode", snapshot.captainSelectionMode().name().toLowerCase());
        response.put("completedTurns", snapshot.completedTurns());
        response.put("decisionHistory", turns);
        response.put("sharedSummary", snapshot.sharedSummary());
        response.put("memoryVersion", snapshot.memoryVersion());
        response.put("activeTurnId", null);
        return response;
    }

    private Map<String, Object> buildPersistedEventsResponse(
            String conversationId,
            String turnId,
            long cursor,
            TeamConversationPersistenceService.PersistedTeamConversationSnapshot snapshot
    ) {
        String resolvedTurnId = RequestUtils.trimOrNull(turnId);
        if (resolvedTurnId == null && !snapshot.turns().isEmpty()) {
            resolvedTurnId = snapshot.turns().get(snapshot.turns().size() - 1).turnId();
        }
        if (resolvedTurnId == null) {
            return buildEventsPage(
                    conversationId,
                    null,
                    cursor,
                    cursor,
                    false,
                    "IDLE",
                    List.of()
            );
        }
        return buildEventsPage(
                conversationId,
                resolvedTurnId,
                cursor,
                cursor,
                true,
                "expired_or_not_found",
                List.of()
        );
    }

    private Map<String, Object> buildPersistedTurnStatus(
            TeamConversationPersistenceService.PersistedTeamTurnSnapshot turn,
            List<String> modelIds
    ) {
        List<Map<String, Object>> memberStatuses = new ArrayList<>();
        Map<String, TeamConversationPersistenceService.PersistedTeamMemberStatus> memberStatusByModel = new LinkedHashMap<>();
        for (TeamConversationPersistenceService.PersistedTeamMemberStatus status : turn.memberStatuses()) {
            memberStatusByModel.put(status.modelId(), status);
        }
        LinkedHashSet<String> orderedModelIds = new LinkedHashSet<>(modelIds);
        orderedModelIds.addAll(memberStatusByModel.keySet());
        for (String modelId : orderedModelIds) {
            TeamConversationPersistenceService.PersistedTeamMemberStatus status = memberStatusByModel.get(modelId);
            Map<String, Object> memberEntry = new LinkedHashMap<>();
            memberEntry.put("modelId", modelId);
            memberEntry.put("proposalStatus", status != null ? status.proposalStatus() : "completed");
            memberEntry.put("debateStatus", status != null ? status.debateStatus() : "completed");
            memberEntry.put("summary", status != null ? status.summary() : null);
            List<Map<String, Object>> debateArguments = new ArrayList<>();
            if (status != null) {
                for (TeamConversationPersistenceService.PersistedTeamDebateArgument argument : status.debateArguments()) {
                    Map<String, Object> argumentEntry = new LinkedHashMap<>();
                    argumentEntry.put("issueId", argument.issueId());
                    argumentEntry.put("argument", argument.argument());
                    argumentEntry.put("stance", argument.stance() != null ? argument.stance() : "");
                    debateArguments.add(argumentEntry);
                }
            }
            memberEntry.put("debateArguments", debateArguments);
            memberStatuses.add(memberEntry);
        }

        List<Map<String, Object>> issues = new ArrayList<>();
        int issueIndex = 1;
        for (TeamConversationPersistenceService.PersistedTeamIssue issue : turn.issues()) {
            issues.add(Map.of(
                    "issueId", issue.issueId() != null ? issue.issueId() : "issue-" + issueIndex,
                    "title", issue.title() != null ? issue.title() : "",
                    "resolved", issue.resolved()
            ));
            issueIndex += 1;
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("turnId", turn.turnId());
        status.put("turnNumber", turn.turnNumber());
        status.put("stage", "COMPLETED");
        status.put("captainModelId", turn.captainModelId());
        status.put("captainSource", turn.captainSource() != null ? turn.captainSource().name().toLowerCase() : null);
        status.put("captainExplanation", null);
        status.put("userMessage", turn.userQuestion());
        status.put("issueCount", turn.issues().size());
        status.put("issues", issues);
        status.put("memberStatuses", memberStatuses);
        status.put("finalAnswer", turn.finalAnswer());
        status.put("errorMessage", null);
        status.put("failedModels", List.of());
        status.put("stageTimestamps", turn.stageTimestamps().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toString(),
                        (left, right) -> right,
                        LinkedHashMap::new
                )));
        return status;
    }

    private boolean isMissingTurn(IllegalStateException error) {
        String message = error.getMessage();
        return message != null && message.startsWith("Debate turn not found or expired:");
    }

    private boolean isMissingConversation(IllegalStateException error) {
        String message = error.getMessage();
        return message != null && message.startsWith("Team conversation not found or expired:");
    }

    private static CaptainSelectionMode parseCaptainMode(String mode) {
        if (mode == null || mode.isBlank() || "auto".equalsIgnoreCase(mode)) {
            return CaptainSelectionMode.AUTO;
        }
        if ("fixed_first".equalsIgnoreCase(mode) || "fixed".equalsIgnoreCase(mode)) {
            return CaptainSelectionMode.FIXED_FIRST;
        }
        return CaptainSelectionMode.AUTO;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : list) {
            String text = str(item);
            if (text != null) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private ApiResponse<Map<String, Object>> validateAllowedModels(UUID userId, String role, List<String> modelIds) {
        Set<String> allowedModels = rolePolicyService.resolveAllowedModels(userId, role);
        if (allowedModels.isEmpty()) {
            return null;
        }
        for (String modelId : modelIds) {
            if (!allowedModels.contains(modelId)) {
                return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "model not available for your role: " + modelId);
            }
        }
        return null;
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
