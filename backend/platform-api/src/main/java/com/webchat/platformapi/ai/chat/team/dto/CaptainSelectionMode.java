package com.webchat.platformapi.ai.chat.team.dto;

/**
 * Captain selection strategy for team conversations.
 */
public enum CaptainSelectionMode {
    /** Scheme C: single-judge initial ranking + Position Flip verification on top-2. */
    AUTO,
    /** First model in the member list is always captain; voting is skipped. */
    FIXED_FIRST
}
