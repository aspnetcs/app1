package com.webchat.platformapi.ai.chat.team.dto;

/**
 * A single model's proposal collected during the COLLECTING stage.
 */
public class MemberProposal {
    private String modelId;
    private String answerText;
    private boolean reasoningSupported;
    private boolean reasoningVisible;
    private String reasoningStatus;
    private Long reasoningDurationMs;
    private String reasoningText;

    public MemberProposal() {}

    public MemberProposal(String modelId, String answerText) {
        this.modelId = modelId;
        this.answerText = answerText;
    }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public boolean isReasoningSupported() { return reasoningSupported; }
    public void setReasoningSupported(boolean reasoningSupported) { this.reasoningSupported = reasoningSupported; }

    public boolean isReasoningVisible() { return reasoningVisible; }
    public void setReasoningVisible(boolean reasoningVisible) { this.reasoningVisible = reasoningVisible; }

    public String getReasoningStatus() { return reasoningStatus; }
    public void setReasoningStatus(String reasoningStatus) { this.reasoningStatus = reasoningStatus; }

    public Long getReasoningDurationMs() { return reasoningDurationMs; }
    public void setReasoningDurationMs(Long reasoningDurationMs) { this.reasoningDurationMs = reasoningDurationMs; }

    public String getReasoningText() { return reasoningText; }
    public void setReasoningText(String reasoningText) { this.reasoningText = reasoningText; }
}
