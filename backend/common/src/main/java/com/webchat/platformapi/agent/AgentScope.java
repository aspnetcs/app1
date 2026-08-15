package com.webchat.platformapi.agent;

/**
 * Visibility scope for an Agent.
 * SYSTEM = public, visible to all users in the marketplace.
 * USER   = private, visible only to the owner.
 */
public enum AgentScope {
    SYSTEM,
    USER;

    public static AgentScope from(String raw) {
        if (raw == null || raw.isBlank()) return SYSTEM;
        try {
            return AgentScope.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SYSTEM;
        }
    }
}
