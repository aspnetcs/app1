package com.webchat.platformapi.ai.chat.team.dto;

/**
 * Five-stage lifecycle of a single debate turn within a team conversation.
 */
public enum DebateStage {
    /** All models generate independent proposals in parallel. */
    COLLECTING,
    /** Anonymous peer-voting to elect the turn captain (or skipped if fixed_first). */
    VOTING,
    /** Captain extracts divergence issues from collected proposals. */
    EXTRACTING,
    /** All models debate each issue; stance updates allowed. */
    DEBATING,
    /** Captain synthesizes divergent views into a final unified answer. */
    SYNTHESIZING,
    /** Turn completed successfully. */
    COMPLETED,
    /** Turn failed or was interrupted. */
    FAILED
}
