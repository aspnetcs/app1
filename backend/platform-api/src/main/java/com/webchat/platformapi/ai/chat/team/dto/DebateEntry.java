package com.webchat.platformapi.ai.chat.team.dto;

/**
 * A single debate entry: one model's response to one issue during DEBATING stage.
 */
public class DebateEntry {
    private String modelId;
    private String issueId;
    private int round;
    private String stance;
    private String argument;
    private boolean stanceChanged;

    public DebateEntry() {}

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public String getStance() { return stance; }
    public void setStance(String stance) { this.stance = stance; }

    public String getArgument() { return argument; }
    public void setArgument(String argument) { this.argument = argument; }

    public boolean isStanceChanged() { return stanceChanged; }
    public void setStanceChanged(boolean stanceChanged) { this.stanceChanged = stanceChanged; }
}
