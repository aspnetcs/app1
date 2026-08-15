package com.webchat.platformapi.ai.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class ConversationPinnedAtRepairRunner {

    private static final Logger log = LoggerFactory.getLogger(ConversationPinnedAtRepairRunner.class);

    private final AiConversationRepository conversationRepository;

    ConversationPinnedAtRepairRunner(AiConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingPinnedAt() {
        List<AiConversationEntity> conversations =
                conversationRepository.findByPinnedTrueAndPinnedAtIsNullAndDeletedAtIsNullAndTemporaryFalse();
        if (conversations.isEmpty()) {
            return;
        }

        for (AiConversationEntity conversation : conversations) {
            conversation.setPinnedAt(ConversationPinOrderSupport.resolveFallbackPinnedAt(conversation));
        }
        conversationRepository.saveAll(conversations);
        log.info("[conversation] backfilled pinnedAt for {} pinned conversations", conversations.size());
    }
}
