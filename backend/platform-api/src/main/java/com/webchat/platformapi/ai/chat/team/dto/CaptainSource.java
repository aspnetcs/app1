package com.webchat.platformapi.ai.chat.team.dto;

/**
 * Records how the captain was determined for a given turn.
 */
public enum CaptainSource {
    /** Elected via Scheme C voting process. */
    AUTO_ELECTED,
    /** Fixed to the first model in the member list. */
    FIXED_FIRST_MODEL,
    /** Voting was skipped or not applicable. */
    SKIPPED
}
