package com.webchat.platformapi.ai.chat.team.dto;

/**
 * A single divergence issue extracted by the captain during EXTRACTING stage.
 */
public class DebateIssue {
    private String issueId;
    private String title;
    private String description;
    private boolean resolved;
    private String resolution;

    public DebateIssue() {}

    public DebateIssue(String issueId, String title, String description) {
        this.issueId = issueId;
        this.title = title;
        this.description = description;
    }

    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
