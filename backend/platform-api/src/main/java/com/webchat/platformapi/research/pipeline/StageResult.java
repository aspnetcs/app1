package com.webchat.platformapi.research.pipeline;

/**
 * Result of a single stage execution.
 */
public record StageResult(
    String status,
    String output,
    boolean requiresApproval,
    String errorMessage
) {
    public static StageResult success(String output) {
        return new StageResult("completed", output, false, null);
    }

    public static StageResult gate(String stageName) {
        return new StageResult("waiting_approval", stageName + " requires review", true, null);
    }

    public static StageResult failure(String error) {
        return new StageResult("failed", null, false, error);
    }

    public boolean isSuccess() {
        return "completed".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status);
    }
}
