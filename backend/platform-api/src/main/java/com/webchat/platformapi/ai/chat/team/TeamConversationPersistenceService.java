package com.webchat.platformapi.ai.chat.team;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.team.dto.CaptainSelectionMode;
import com.webchat.platformapi.ai.chat.team.dto.CaptainSource;
import com.webchat.platformapi.ai.chat.team.dto.DebateStage;
import com.webchat.platformapi.ai.chat.team.dto.DebateTurnSession;
import com.webchat.platformapi.ai.chat.team.dto.MemberProposal;
import com.webchat.platformapi.ai.chat.team.dto.TeamConversationContext;
import com.webchat.platformapi.ai.conversation.AiConversationEntity;
import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.ai.conversation.AiMessageEntity;
import com.webchat.platformapi.ai.conversation.AiMessageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class TeamConversationPersistenceService {

    private static final int DEFAULT_SHARED_SUMMARY_MAX_CHARS = 2000;
    private static final int MAX_PERSISTED_TEAM_TURNS = 20;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public TeamConversationPersistenceService(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public void ensureConversationRecord(
            String conversationId,
            String userId,
            List<String> modelIds,
            CaptainSelectionMode captainSelectionMode,
            String title
    ) {
        UUID conversationUuid = UUID.fromString(conversationId);
        UUID userUuid = UUID.fromString(userId);
        AiConversationEntity conversation = conversationRepository.findById(conversationUuid).orElseGet(AiConversationEntity::new);
        if (conversation.getId() != null && conversation.getUserId() != null && !userUuid.equals(conversation.getUserId())) {
            throw new SecurityException("user does not own team conversation: " + conversationId);
        }

        conversation.setId(conversationUuid);
        conversation.setUserId(userUuid);
        conversation.setDeletedAt(null);
        conversation.setTemporary(false);
        conversation.setMode("team");
        conversation.setCaptainSelectionMode(normalizeCaptainSelectionMode(captainSelectionMode));
        conversation.setCompareModelsJson(toJsonArray(modelIds));
        if ((conversation.getModel() == null || conversation.getModel().isBlank()) && modelIds != null && !modelIds.isEmpty()) {
            conversation.setModel(modelIds.get(0));
        }
        String normalizedTitle = safeTrim(title);
        if (normalizedTitle != null) {
            conversation.setTitle(normalizedTitle);
        } else if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle("Team chat");
        }
        conversationRepository.save(conversation);
    }

    public void persistCompletedTurn(TeamConversationContext context, DebateTurnSession turn) {
        ensureConversationRecord(
                context.getConversationId(),
                context.getUserId(),
                context.getSelectedModelIds(),
                context.getCaptainSelectionMode(),
                buildConversationTitle(turn.getUserMessage())
        );

        UUID conversationUuid = UUID.fromString(context.getConversationId());
        UUID multiRoundUuid = UUID.fromString(turn.getTurnId());
        if (messageRepository.countByConversationIdAndMultiRoundId(conversationUuid, multiRoundUuid) == 0) {
            AiMessageEntity userMessage = new AiMessageEntity();
            userMessage.setConversationId(conversationUuid);
            userMessage.setRole("user");
            userMessage.setContent(turn.getUserMessage());
            userMessage.setContentType("text");
            userMessage.setMultiRoundId(multiRoundUuid);
            userMessage.setBranchIndex(0);
            userMessage.setVersion(1);

            AiMessageEntity assistantMessage = new AiMessageEntity();
            assistantMessage.setConversationId(conversationUuid);
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(turn.getFinalAnswer());
            assistantMessage.setContentType("text");
            assistantMessage.setModel(turn.getCaptainModelId());
            assistantMessage.setMultiRoundId(multiRoundUuid);
            assistantMessage.setBranchIndex(0);
            assistantMessage.setVersion(1);

            messageRepository.saveAll(List.of(userMessage, assistantMessage));
        }

        conversationRepository.findById(conversationUuid).ifPresent(conversation -> {
            List<PersistedTeamTurnSnapshot> turns = new ArrayList<>(parseTeamTurnsJson(conversation.getTeamTurnsJson()));
            PersistedTeamTurnSnapshot currentTurn = buildPersistedTurnSnapshot(turn, context.getSelectedModelIds());
            turns.removeIf(existing -> Objects.equals(existing.turnId(), currentTurn.turnId()));
            turns.add(currentTurn);
            if (turns.size() > MAX_PERSISTED_TEAM_TURNS) {
                turns = new ArrayList<>(turns.subList(turns.size() - MAX_PERSISTED_TEAM_TURNS, turns.size()));
            }
            conversation.setTeamTurnsJson(writeTeamTurnsJson(turns));
            conversationRepository.save(conversation);
        });
    }

    public Optional<PersistedTeamConversationSnapshot> loadPersistedConversation(String conversationId, String userId) {
        UUID conversationUuid = UUID.fromString(conversationId);
        UUID userUuid = UUID.fromString(userId);
        AiConversationEntity conversation = loadAccessibleTeamConversation(conversationUuid, userUuid);
        if (conversation == null) {
            return Optional.empty();
        }

        List<String> modelIds = parseModelIds(conversation.getCompareModelsJson());
        if (modelIds.isEmpty()) {
            String fallbackModel = safeTrim(conversation.getModel());
            if (fallbackModel != null) {
                modelIds = List.of(fallbackModel);
            }
        }

        CaptainSelectionMode captainSelectionMode = normalizeCaptainSelectionMode(conversation.getCaptainSelectionMode());
        CaptainSource captainSource = captainSelectionMode == CaptainSelectionMode.FIXED_FIRST
                ? CaptainSource.FIXED_FIRST_MODEL
                : CaptainSource.AUTO_ELECTED;

        List<PersistedTeamTurnSnapshot> turns = parseTeamTurnsJson(conversation.getTeamTurnsJson());
        if (turns.isEmpty()) {
            turns = loadLegacyPersistedTurns(conversationUuid, modelIds, captainSource);
        }

        List<String> captainHistory = turns.stream()
                .map(PersistedTeamTurnSnapshot::captainModelId)
                .filter(Objects::nonNull)
                .toList();
        String sharedSummary = buildFallbackSharedSummary(turns);

        return Optional.of(new PersistedTeamConversationSnapshot(
                conversationId,
                conversation.getTitle(),
                List.copyOf(modelIds),
                captainSelectionMode,
                turns.size(),
                sharedSummary,
                turns.size(),
                captainHistory,
                turns
        ));
    }

    public Optional<TeamConversationContext> restoreConversationContext(String conversationId, String userId) {
        Optional<PersistedTeamConversationSnapshot> snapshotOptional = loadPersistedConversation(conversationId, userId);
        if (snapshotOptional.isEmpty()) {
            return Optional.empty();
        }

        PersistedTeamConversationSnapshot snapshot = snapshotOptional.orElseThrow();
        TeamConversationContext context = new TeamConversationContext();
        context.setConversationId(snapshot.conversationId());
        context.setUserId(userId);
        context.setSelectedModelIds(new ArrayList<>(snapshot.modelIds()));
        context.setCaptainSelectionMode(snapshot.captainSelectionMode());
        context.setSharedSummary(snapshot.sharedSummary());
        context.setCaptainHandoffSummary(buildCaptainHandoffSummary(snapshot.turns()));
        context.setDecisionHistory(snapshot.turns().stream().map(this::toDecisionRecord).toList());
        context.setCaptainHistory(new ArrayList<>(snapshot.captainHistory()));
        context.setCompletedTurns(snapshot.completedTurns());
        context.setMemoryVersion(snapshot.memoryVersion());
        context.setCreatedAt(Instant.now());
        context.setLastActiveAt(Instant.now());
        context.setActiveTurnId(null);
        context.setOpenIssues(new ArrayList<>());
        context.setMemberMemories(new LinkedHashMap<>());
        return Optional.of(context);
    }

    private TeamConversationContext.DecisionRecord toDecisionRecord(PersistedTeamTurnSnapshot turn) {
        TeamConversationContext.DecisionRecord record = new TeamConversationContext.DecisionRecord();
        record.setTurnId(turn.turnId());
        record.setTurnNumber(turn.turnNumber());
        record.setCaptainModelId(turn.captainModelId());
        record.setCaptainSource(turn.captainSource());
        record.setUserQuestion(truncate(turn.userQuestion(), 200));
        record.setFinalAnswerSummary(truncate(turn.finalAnswer(), 500));
        record.setKeyIssues(turn.issues().stream()
                .map(PersistedTeamIssue::title)
                .filter(Objects::nonNull)
                .toList());
        record.setTimestamp(turn.timestamp());
        return record;
    }

    private List<PersistedTeamTurnSnapshot> loadLegacyPersistedTurns(
            UUID conversationUuid,
            List<String> modelIds,
            CaptainSource captainSource
    ) {
        LinkedHashMap<UUID, PersistedTurnAccumulator> groupedTurns = new LinkedHashMap<>();
        for (AiMessageEntity message : messageRepository.findByConversationIdAndParentMessageIdIsNullOrderByCreatedAtAsc(conversationUuid)) {
            UUID multiRoundId = message.getMultiRoundId();
            if (multiRoundId == null) {
                continue;
            }
            PersistedTurnAccumulator accumulator = groupedTurns.computeIfAbsent(
                    multiRoundId,
                    ignored -> new PersistedTurnAccumulator(multiRoundId.toString())
            );
            if ("user".equalsIgnoreCase(message.getRole())) {
                accumulator.userQuestion = preferExisting(accumulator.userQuestion, message.getContent());
                if (accumulator.timestamp == null) {
                    accumulator.timestamp = message.getCreatedAt();
                }
            } else if ("assistant".equalsIgnoreCase(message.getRole())) {
                accumulator.finalAnswer = preferExisting(accumulator.finalAnswer, message.getContent());
                accumulator.captainModelId = preferExisting(accumulator.captainModelId, message.getModel());
                accumulator.timestamp = message.getCreatedAt() != null ? message.getCreatedAt() : accumulator.timestamp;
            }
        }

        List<PersistedTeamTurnSnapshot> turns = new ArrayList<>();
        int turnNumber = 1;
        for (PersistedTurnAccumulator accumulator : groupedTurns.values()) {
            if (accumulator.userQuestion == null && accumulator.finalAnswer == null) {
                continue;
            }
            turns.add(new PersistedTeamTurnSnapshot(
                    accumulator.turnId,
                    turnNumber,
                    accumulator.captainModelId != null
                            ? accumulator.captainModelId
                            : (modelIds.isEmpty() ? null : modelIds.get(0)),
                    captainSource,
                    accumulator.userQuestion != null ? accumulator.userQuestion : "",
                    accumulator.finalAnswer,
                    List.of(),
                    List.of(),
                    accumulator.timestamp,
                    Map.of()
            ));
            turnNumber += 1;
        }
        return turns;
    }

    private AiConversationEntity loadAccessibleTeamConversation(UUID conversationId, UUID userId) {
        AiConversationEntity conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || conversation.getDeletedAt() != null) {
            return null;
        }
        if (conversation.getUserId() != null && !userId.equals(conversation.getUserId())) {
            throw new SecurityException("user does not own team conversation: " + conversationId);
        }
        if (!"team".equalsIgnoreCase(safeTrim(conversation.getMode()))) {
            return null;
        }
        return conversation;
    }

    private List<String> parseModelIds(String rawJson) {
        String normalized = safeTrim(rawJson);
        if (normalized == null) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(normalized, new TypeReference<List<String>>() {});
            LinkedHashSet<String> deduped = new LinkedHashSet<>();
            for (String value : parsed) {
                String modelId = safeTrim(value);
                if (modelId != null) {
                    deduped.add(modelId);
                }
            }
            return List.copyOf(deduped);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize team conversation model ids", e);
        }
    }

    private CaptainSelectionMode normalizeCaptainSelectionMode(String raw) {
        if ("fixed_first".equalsIgnoreCase(raw)) {
            return CaptainSelectionMode.FIXED_FIRST;
        }
        return CaptainSelectionMode.AUTO;
    }

    private String normalizeCaptainSelectionMode(CaptainSelectionMode mode) {
        return mode == CaptainSelectionMode.FIXED_FIRST ? "fixed_first" : "auto";
    }

    private String buildConversationTitle(String userMessage) {
        String normalized = safeTrim(userMessage);
        if (normalized == null) {
            return "Team chat";
        }
        return truncate(normalized, 50);
    }

    private String buildFallbackSharedSummary(List<PersistedTeamTurnSnapshot> turns) {
        if (turns.isEmpty()) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        int startIndex = Math.max(0, turns.size() - 3);
        for (int i = startIndex; i < turns.size(); i += 1) {
            PersistedTeamTurnSnapshot turn = turns.get(i);
            if (summary.length() > 0) {
                summary.append("\n\n");
            }
            summary.append("Q: ").append(truncate(turn.userQuestion(), 200));
            if (turn.finalAnswer() != null && !turn.finalAnswer().isBlank()) {
                summary.append("\nA: ").append(truncate(turn.finalAnswer(), 500));
            }
        }
        return truncate(summary.toString(), DEFAULT_SHARED_SUMMARY_MAX_CHARS);
    }

    private String buildCaptainHandoffSummary(List<PersistedTeamTurnSnapshot> turns) {
        if (turns.isEmpty()) {
            return null;
        }
        PersistedTeamTurnSnapshot latestTurn = turns.get(turns.size() - 1);
        return "Captain: " + (latestTurn.captainModelId() != null ? latestTurn.captainModelId() : "unknown")
                + " (" + latestTurn.captainSource() + "). Final answer delivered.";
    }

    private String preferExisting(String existing, String next) {
        return existing != null && !existing.isBlank() ? existing : safeTrim(next);
    }

    private PersistedTeamTurnSnapshot buildPersistedTurnSnapshot(DebateTurnSession turn, List<String> selectedModelIds) {
        List<PersistedTeamIssue> issues = new ArrayList<>();
        for (int index = 0; index < turn.getIssues().size(); index += 1) {
            var issue = turn.getIssues().get(index);
            issues.add(new PersistedTeamIssue(
                    safeTrim(issue.getIssueId()) != null ? safeTrim(issue.getIssueId()) : "issue-" + (index + 1),
                    safeTrim(issue.getTitle()) != null ? safeTrim(issue.getTitle()) : "",
                    issue.isResolved()
            ));
        }

        LinkedHashMap<String, List<PersistedTeamDebateArgument>> debateArgumentsByModel = new LinkedHashMap<>();
        for (var entry : turn.getDebateEntries()) {
            String modelId = safeTrim(entry.getModelId());
            String issueId = safeTrim(entry.getIssueId());
            String argument = safeTrim(entry.getArgument());
            if (modelId == null || issueId == null || argument == null) {
                continue;
            }
            debateArgumentsByModel.computeIfAbsent(modelId, ignored -> new ArrayList<>())
                    .add(new PersistedTeamDebateArgument(
                            issueId,
                            argument,
                            safeTrim(entry.getStance())
                    ));
        }

        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        if (selectedModelIds != null) {
            modelIds.addAll(selectedModelIds);
        }
        modelIds.addAll(turn.getProposalStatuses().keySet());
        modelIds.addAll(turn.getDebateStatuses().keySet());
        modelIds.addAll(turn.getProposals().keySet());
        modelIds.addAll(debateArgumentsByModel.keySet());
        modelIds.addAll(turn.getFailedModels());

        List<PersistedTeamMemberStatus> memberStatuses = new ArrayList<>();
        for (String modelId : modelIds) {
            MemberProposal proposal = turn.getProposals().get(modelId);
            memberStatuses.add(new PersistedTeamMemberStatus(
                    modelId,
                    safeTrim(turn.getProposalStatuses().get(modelId)) != null
                            ? safeTrim(turn.getProposalStatuses().get(modelId))
                            : "pending",
                    safeTrim(turn.getDebateStatuses().get(modelId)) != null
                            ? safeTrim(turn.getDebateStatuses().get(modelId))
                            : "pending",
                    proposal != null ? safeTrim(proposal.getAnswerText()) : null,
                    List.copyOf(debateArgumentsByModel.getOrDefault(modelId, List.of()))
            ));
        }

        return new PersistedTeamTurnSnapshot(
                turn.getTurnId(),
                turn.getTurnNumber(),
                safeTrim(turn.getCaptainModelId()),
                turn.getCaptainSource() != null ? turn.getCaptainSource() : CaptainSource.AUTO_ELECTED,
                turn.getUserMessage() != null ? turn.getUserMessage() : "",
                turn.getFinalAnswer(),
                List.copyOf(issues),
                List.copyOf(memberStatuses),
                resolveTurnTimestamp(turn),
                resolvePersistedStageTimestamps(turn)
        );
    }

    private Instant resolveTurnTimestamp(DebateTurnSession turn) {
        Instant completedAt = turn.getStageTimestamps().get(DebateStage.COMPLETED.name());
        if (completedAt != null) {
            return completedAt;
        }
        Instant failedAt = turn.getStageTimestamps().get(DebateStage.FAILED.name());
        if (failedAt != null) {
            return failedAt;
        }
        return turn.getStageTimestamps().values().stream()
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElseGet(Instant::now);
    }

    private Map<String, Instant> resolvePersistedStageTimestamps(DebateTurnSession turn) {
        if (turn.getStageTimestamps() == null || turn.getStageTimestamps().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Instant> stageTimestamps = new LinkedHashMap<>();
        turn.getStageTimestamps().forEach((stage, timestamp) -> {
            String normalizedStage = safeTrim(stage);
            if (normalizedStage != null && timestamp != null) {
                stageTimestamps.put(normalizedStage, timestamp);
            }
        });
        return Map.copyOf(stageTimestamps);
    }

    private List<PersistedTeamTurnSnapshot> parseTeamTurnsJson(String rawJson) {
        String normalized = safeTrim(rawJson);
        if (normalized == null) {
            return List.of();
        }
        try {
            List<PersistedTeamTurnSnapshot> parsed = objectMapper.readValue(
                    normalized,
                    new TypeReference<List<PersistedTeamTurnSnapshot>>() {}
            );
            List<PersistedTeamTurnSnapshot> normalizedTurns = new ArrayList<>();
            for (PersistedTeamTurnSnapshot turn : parsed) {
                PersistedTeamTurnSnapshot snapshot = normalizePersistedTurnSnapshot(turn);
                if (snapshot != null) {
                    normalizedTurns.add(snapshot);
                }
            }
            return normalizedTurns;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeTeamTurnsJson(List<PersistedTeamTurnSnapshot> turns) {
        try {
            return objectMapper.writeValueAsString(turns == null ? List.of() : turns);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize persisted team turns", e);
        }
    }

    private PersistedTeamTurnSnapshot normalizePersistedTurnSnapshot(PersistedTeamTurnSnapshot turn) {
        if (turn == null) {
            return null;
        }
        String turnId = safeTrim(turn.turnId());
        if (turnId == null) {
            return null;
        }
        List<PersistedTeamIssue> issues = new ArrayList<>();
        List<PersistedTeamIssue> rawIssues = turn.issues() != null ? turn.issues() : List.of();
        for (int index = 0; index < rawIssues.size(); index += 1) {
            PersistedTeamIssue issue = rawIssues.get(index);
            if (issue == null) {
                continue;
            }
            issues.add(new PersistedTeamIssue(
                    safeTrim(issue.issueId()) != null ? safeTrim(issue.issueId()) : "issue-" + (index + 1),
                    safeTrim(issue.title()) != null ? safeTrim(issue.title()) : "",
                    issue.resolved()
                ));
        }
        List<PersistedTeamMemberStatus> memberStatuses = new ArrayList<>();
        List<PersistedTeamMemberStatus> rawStatuses = turn.memberStatuses() != null ? turn.memberStatuses() : List.of();
        for (PersistedTeamMemberStatus status : rawStatuses) {
            if (status == null || safeTrim(status.modelId()) == null) {
                continue;
            }
            List<PersistedTeamDebateArgument> arguments = new ArrayList<>();
            List<PersistedTeamDebateArgument> rawArguments =
                    status.debateArguments() != null ? status.debateArguments() : List.of();
            for (PersistedTeamDebateArgument argument : rawArguments) {
                if (argument == null || safeTrim(argument.issueId()) == null || safeTrim(argument.argument()) == null) {
                    continue;
                }
                arguments.add(new PersistedTeamDebateArgument(
                        safeTrim(argument.issueId()),
                        safeTrim(argument.argument()),
                        safeTrim(argument.stance())
                ));
            }
            memberStatuses.add(new PersistedTeamMemberStatus(
                    safeTrim(status.modelId()),
                    safeTrim(status.proposalStatus()) != null ? safeTrim(status.proposalStatus()) : "pending",
                    safeTrim(status.debateStatus()) != null ? safeTrim(status.debateStatus()) : "pending",
                    safeTrim(status.summary()),
                    List.copyOf(arguments)
            ));
        }
        return new PersistedTeamTurnSnapshot(
                turnId,
                Math.max(1, turn.turnNumber()),
                safeTrim(turn.captainModelId()),
                turn.captainSource() != null ? turn.captainSource() : CaptainSource.AUTO_ELECTED,
                turn.userQuestion() != null ? turn.userQuestion() : "",
                turn.finalAnswer(),
                List.copyOf(issues),
                List.copyOf(memberStatuses),
                turn.timestamp(),
                normalizePersistedStageTimestamps(turn.stageTimestamps())
        );
    }

    private Map<String, Instant> normalizePersistedStageTimestamps(Map<String, Instant> rawStageTimestamps) {
        if (rawStageTimestamps == null || rawStageTimestamps.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Instant> normalized = new LinkedHashMap<>();
        rawStageTimestamps.forEach((stage, timestamp) -> {
            String normalizedStage = safeTrim(stage);
            if (normalizedStage != null && timestamp != null) {
                normalized.put(normalizedStage, timestamp);
            }
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private String truncate(String value, int maxChars) {
        String normalized = safeTrim(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record PersistedTeamIssue(
            String issueId,
            String title,
            boolean resolved
    ) {
    }

    public record PersistedTeamDebateArgument(
            String issueId,
            String argument,
            String stance
    ) {
    }

    public record PersistedTeamMemberStatus(
            String modelId,
            String proposalStatus,
            String debateStatus,
            String summary,
            List<PersistedTeamDebateArgument> debateArguments
    ) {
    }

    public record PersistedTeamTurnSnapshot(
            String turnId,
            int turnNumber,
            String captainModelId,
            CaptainSource captainSource,
            String userQuestion,
            String finalAnswer,
            List<PersistedTeamIssue> issues,
            List<PersistedTeamMemberStatus> memberStatuses,
            Instant timestamp,
            Map<String, Instant> stageTimestamps
    ) {
    }

    public record PersistedTeamConversationSnapshot(
            String conversationId,
            String title,
            List<String> modelIds,
            CaptainSelectionMode captainSelectionMode,
            int completedTurns,
            String sharedSummary,
            int memoryVersion,
            List<String> captainHistory,
            List<PersistedTeamTurnSnapshot> turns
    ) {
    }

    private static final class PersistedTurnAccumulator {
        private final String turnId;
        private String captainModelId;
        private String userQuestion;
        private String finalAnswer;
        private Instant timestamp;

        private PersistedTurnAccumulator(String turnId) {
            this.turnId = turnId;
        }
    }
}
