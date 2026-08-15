package com.webchat.platformapi.ai.chat.team.dto;

import java.util.List;

/**
 * Records the full Scheme C voting process for a single turn.
 * <p>
 * Scheme C: 1 single-judge anonymous initial ranking + 2 Position Flip
 * verification calls on the top-2 candidates. Fixed 3 LLM calls total.
 */
public class VotingRecord {
    /** Model used as the single anonymous judge for initial ranking. */
    private String judgeModelId;

    /** The full ranked list from the initial judge call. */
    private List<String> initialRanking;

    /** Position Flip verification result: judge sees [A, B] ordering. */
    private String verificationResultAB;

    /** Position Flip verification result: judge sees [B, A] ordering. */
    private String verificationResultBA;

    /** Whether verification agreed with the initial ranking. */
    private boolean verificationConsistent;

    /** Final elected captain model ID after conflict resolution. */
    private String electedCaptainModelId;

    /** Human-readable explanation of the final decision. */
    private String decisionExplanation;

    public VotingRecord() {}

    public String getJudgeModelId() { return judgeModelId; }
    public void setJudgeModelId(String judgeModelId) { this.judgeModelId = judgeModelId; }

    public List<String> getInitialRanking() { return initialRanking; }
    public void setInitialRanking(List<String> initialRanking) { this.initialRanking = initialRanking; }

    public String getVerificationResultAB() { return verificationResultAB; }
    public void setVerificationResultAB(String verificationResultAB) { this.verificationResultAB = verificationResultAB; }

    public String getVerificationResultBA() { return verificationResultBA; }
    public void setVerificationResultBA(String verificationResultBA) { this.verificationResultBA = verificationResultBA; }

    public boolean isVerificationConsistent() { return verificationConsistent; }
    public void setVerificationConsistent(boolean verificationConsistent) { this.verificationConsistent = verificationConsistent; }

    public String getElectedCaptainModelId() { return electedCaptainModelId; }
    public void setElectedCaptainModelId(String electedCaptainModelId) { this.electedCaptainModelId = electedCaptainModelId; }

    public String getDecisionExplanation() { return decisionExplanation; }
    public void setDecisionExplanation(String decisionExplanation) { this.decisionExplanation = decisionExplanation; }
}
