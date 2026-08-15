package com.webchat.platformapi.backup;

public record UserBackupLimits(int conversationLimit, int messageLimitPerConversation) {

    private static final int DEFAULT_CONVERSATION_LIMIT = 50;
    private static final int DEFAULT_MESSAGE_LIMIT = 200;

    public UserBackupLimits(Integer conversationLimit, Integer messageLimitPerConversation) {
        this(
                normalize(conversationLimit, DEFAULT_CONVERSATION_LIMIT, 1, 200),
                normalize(messageLimitPerConversation, DEFAULT_MESSAGE_LIMIT, 1, 2000)
        );
    }

    private static int normalize(Integer value, int fallback, int min, int max) {
        if (value == null) return fallback;
        int v = value;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}

