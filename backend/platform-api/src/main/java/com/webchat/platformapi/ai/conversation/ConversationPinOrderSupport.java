package com.webchat.platformapi.ai.conversation;

import java.time.Instant;

final class ConversationPinOrderSupport {

    private ConversationPinOrderSupport() {
    }

    static Instant resolvePinnedSortKey(AiConversationEntity conversation) {
        if (!conversation.isPinned()) {
            return null;
        }
        return resolveFallbackPinnedAt(conversation);
    }

    static Instant resolveFallbackPinnedAt(AiConversationEntity conversation) {
        if (conversation.getPinnedAt() != null) {
            return conversation.getPinnedAt();
        }
        if (conversation.getUpdatedAt() != null) {
            return conversation.getUpdatedAt();
        }
        if (conversation.getCreatedAt() != null) {
            return conversation.getCreatedAt();
        }
        return Instant.EPOCH;
    }
}
