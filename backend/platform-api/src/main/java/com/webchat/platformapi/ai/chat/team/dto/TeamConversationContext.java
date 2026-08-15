package com.webchat.platformapi.ai.chat.team.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-lived team conversation context that spans multiple debate turns.
 * Persisted in Redis with a long TTL to support multi-turn follow-up questions.
 * <p>
 * Key invariant: this object NEVER stores raw reasoning text from any model.
 * Only compressed summaries, decision history, and member memory snapshots are kept.
 */
public class TeamConversationContext {
    private String conversationId;
    private String userId;
    private List<String> selectedModelIds = new ArrayList<>();
    private List<String> knowledgeBaseIds = new ArrayList<>();
    private CaptainSelectionMode captainSelectionMode = CaptainSelectionMode.AUTO;

    /** Compressed shared summary of all prior turns. */
    private String sharedSummary;

    /** Captain handoff summary from the latest completed turn. */
    private String captainHandoffSummary;

    /** Per-model compressed memory across turns. Key: modelId. */
    private Map<String, String> memberMemories = new HashMap<>();

    /** Unresolved issues carried forward from prior turns. */
    private List<String> openIssues = new ArrayList<>();

    /** Sequential log of decisions from prior turns. */
    private List<DecisionRecord> decisionHistory = new ArrayList<>();

    /** Captain model IDs from prior turns (index = turn number - 1). */
    private List<String> captainHistory = new ArrayList<>();

    private Instant createdAt;
    private Instant lastActiveAt;

    /** Monotonically increasing version for optimistic concurrency control. */
    private int memoryVersion;

    /** Total number of completed turns in this conversation. */
    private int completedTurns;

    /** Latest active turn for refresh/status recovery. */
    private String activeTurnId;

    public TeamConversationContext() {}

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<String> getSelectedModelIds() { return selectedModelIds; }
    public void setSelectedModelIds(List<String> selectedModelIds) { this.selectedModelIds = selectedModelIds; }

    public List<String> getKnowledgeBaseIds() { return knowledgeBaseIds; }
    public void setKnowledgeBaseIds(List<String> knowledgeBaseIds) { this.knowledgeBaseIds = knowledgeBaseIds; }

    public CaptainSelectionMode getCaptainSelectionMode() { return captainSelectionMode; }
    public void setCaptainSelectionMode(CaptainSelectionMode captainSelectionMode) { this.captainSelectionMode = captainSelectionMode; }

    public String getSharedSummary() { return sharedSummary; }
    public void setSharedSummary(String sharedSummary) { this.sharedSummary = sharedSummary; }

    public String getCaptainHandoffSummary() { return captainHandoffSummary; }
    public void setCaptainHandoffSummary(String captainHandoffSummary) { this.captainHandoffSummary = captainHandoffSummary; }

    public Map<String, String> getMemberMemories() { return memberMemories; }
    public void setMemberMemories(Map<String, String> memberMemories) { this.memberMemories = memberMemories; }

    public List<String> getOpenIssues() { return openIssues; }
    public void setOpenIssues(List<String> openIssues) { this.openIssues = openIssues; }

    public List<DecisionRecord> getDecisionHistory() { return decisionHistory; }
    public void setDecisionHistory(List<DecisionRecord> decisionHistory) { this.decisionHistory = decisionHistory; }

    public List<String> getCaptainHistory() { return captainHistory; }
    public void setCaptainHistory(List<String> captainHistory) { this.captainHistory = captainHistory; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public int getMemoryVersion() { return memoryVersion; }
    public void setMemoryVersion(int memoryVersion) { this.memoryVersion = memoryVersion; }

    public int getCompletedTurns() { return completedTurns; }
    public void setCompletedTurns(int completedTurns) { this.completedTurns = completedTurns; }

    public String getActiveTurnId() { return activeTurnId; }
    public void setActiveTurnId(String activeTurnId) { this.activeTurnId = activeTurnId; }

    /**
     * Compact record of a single turn's outcome for long-term history.
     */
    public static class DecisionRecord {
        private String turnId;
        private int turnNumber;
        private String captainModelId;
        private CaptainSource captainSource;
        private String userQuestion;
        private String finalAnswerSummary;
        private List<String> keyIssues = new ArrayList<>();
        private Instant timestamp;

        public DecisionRecord() {}

        public String getTurnId() { return turnId; }
        public void setTurnId(String turnId) { this.turnId = turnId; }

        public int getTurnNumber() { return turnNumber; }
        public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }

        public String getCaptainModelId() { return captainModelId; }
        public void setCaptainModelId(String captainModelId) { this.captainModelId = captainModelId; }

        public CaptainSource getCaptainSource() { return captainSource; }
        public void setCaptainSource(CaptainSource captainSource) { this.captainSource = captainSource; }

        public String getUserQuestion() { return userQuestion; }
        public void setUserQuestion(String userQuestion) { this.userQuestion = userQuestion; }

        public String getFinalAnswerSummary() { return finalAnswerSummary; }
        public void setFinalAnswerSummary(String finalAnswerSummary) { this.finalAnswerSummary = finalAnswerSummary; }

        public List<String> getKeyIssues() { return keyIssues; }
        public void setKeyIssues(List<String> keyIssues) { this.keyIssues = keyIssues; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }
}
