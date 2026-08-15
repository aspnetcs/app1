package com.webchat.platformapi.ai.chat.team.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Short-lived session for a single debate turn (one user message -> five-stage consensus).
 * Persisted in Redis with a shorter TTL than TeamConversationContext.
 * <p>
 * After completion, compressed results are written back to TeamConversationContext;
 * this object may expire and be garbage-collected.
 */
public class DebateTurnSession {
    private String turnId;
    private String conversationId;
    private String userId;
    private int turnNumber;
    private DebateStage stage = DebateStage.COLLECTING;

    private CaptainSelectionMode captainSelectionMode;
    private CaptainSource captainSource;
    private String captainModelId;
    private String userMessage;

    /** Proposals collected during COLLECTING stage. Key: modelId. */
    private Map<String, MemberProposal> proposals = new HashMap<>();

    /** Per-model proposal progress for UI polling fallback. */
    private Map<String, String> proposalStatuses = new HashMap<>();

    /** Voting record from VOTING stage (null if skipped). */
    private VotingRecord votingRecord;

    /** Issues extracted by captain during EXTRACTING stage. */
    private List<DebateIssue> issues = new ArrayList<>();

    /** Debate entries across all rounds during DEBATING stage. */
    private List<DebateEntry> debateEntries = new ArrayList<>();

    /** Per-model debate progress for UI polling fallback. */
    private Map<String, String> debateStatuses = new HashMap<>();

    /** Final synthesized answer from SYNTHESIZING stage. */
    private String finalAnswer;

    /** Captain's handoff summary for the next turn's context reconstruction. */
    private String captainHandoffSummary;

    /** Per-stage timestamps for latency tracking. */
    private Map<String, Instant> stageTimestamps = new HashMap<>();

    /** Error message if the turn failed. */
    private String errorMessage;

    /** Model IDs that failed during this turn (for partial failure handling). */
    private List<String> failedModels = new ArrayList<>();

    public DebateTurnSession() {}

    // --- Stage transition ---

    /**
     * Advance to the next stage. Throws if the transition is illegal.
     */
    public void advanceTo(DebateStage next) {
        if (stage == DebateStage.COMPLETED || stage == DebateStage.FAILED) {
            throw new IllegalStateException("Cannot advance from terminal stage: " + stage);
        }
        if (next.ordinal() <= stage.ordinal() && next != DebateStage.FAILED) {
            throw new IllegalStateException("Illegal backward transition: " + stage + " -> " + next);
        }
        this.stageTimestamps.put(next.name(), Instant.now());
        this.stage = next;
    }

    /**
     * Mark the turn as failed with an error message.
     */
    public void fail(String error) {
        this.errorMessage = error;
        this.stageTimestamps.put(DebateStage.FAILED.name(), Instant.now());
        this.stage = DebateStage.FAILED;
        LinkedHashSet<String> failed = new LinkedHashSet<>(failedModels);
        for (Map.Entry<String, String> entry : proposalStatuses.entrySet()) {
            if ("pending".equalsIgnoreCase(entry.getValue())) {
                entry.setValue("failed");
            }
            if ("failed".equalsIgnoreCase(entry.getValue()) || "timeout".equalsIgnoreCase(entry.getValue())) {
                failed.add(entry.getKey());
            }
        }
        for (Map.Entry<String, String> entry : debateStatuses.entrySet()) {
            if ("pending".equalsIgnoreCase(entry.getValue())) {
                entry.setValue("failed");
            }
            if ("failed".equalsIgnoreCase(entry.getValue()) || "timeout".equalsIgnoreCase(entry.getValue())) {
                failed.add(entry.getKey());
            }
        }
        this.failedModels = new ArrayList<>(failed);
    }

    // --- Getters / Setters ---

    public String getTurnId() { return turnId; }
    public void setTurnId(String turnId) { this.turnId = turnId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }

    public DebateStage getStage() { return stage; }
    public void setStage(DebateStage stage) { this.stage = stage; }

    public CaptainSelectionMode getCaptainSelectionMode() { return captainSelectionMode; }
    public void setCaptainSelectionMode(CaptainSelectionMode captainSelectionMode) { this.captainSelectionMode = captainSelectionMode; }

    public CaptainSource getCaptainSource() { return captainSource; }
    public void setCaptainSource(CaptainSource captainSource) { this.captainSource = captainSource; }

    public String getCaptainModelId() { return captainModelId; }
    public void setCaptainModelId(String captainModelId) { this.captainModelId = captainModelId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public Map<String, MemberProposal> getProposals() { return proposals; }
    public void setProposals(Map<String, MemberProposal> proposals) { this.proposals = proposals; }

    public Map<String, String> getProposalStatuses() { return proposalStatuses; }
    public void setProposalStatuses(Map<String, String> proposalStatuses) { this.proposalStatuses = proposalStatuses; }

    public VotingRecord getVotingRecord() { return votingRecord; }
    public void setVotingRecord(VotingRecord votingRecord) { this.votingRecord = votingRecord; }

    public List<DebateIssue> getIssues() { return issues; }
    public void setIssues(List<DebateIssue> issues) { this.issues = issues; }

    public List<DebateEntry> getDebateEntries() { return debateEntries; }
    public void setDebateEntries(List<DebateEntry> debateEntries) { this.debateEntries = debateEntries; }

    public Map<String, String> getDebateStatuses() { return debateStatuses; }
    public void setDebateStatuses(Map<String, String> debateStatuses) { this.debateStatuses = debateStatuses; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public String getCaptainHandoffSummary() { return captainHandoffSummary; }
    public void setCaptainHandoffSummary(String captainHandoffSummary) { this.captainHandoffSummary = captainHandoffSummary; }

    public Map<String, Instant> getStageTimestamps() { return stageTimestamps; }
    public void setStageTimestamps(Map<String, Instant> stageTimestamps) { this.stageTimestamps = stageTimestamps; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<String> getFailedModels() { return failedModels; }
    public void setFailedModels(List<String> failedModels) { this.failedModels = failedModels; }
}
