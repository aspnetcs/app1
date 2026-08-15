package com.webchat.platformapi.ai.conversation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiConversationForkService {

    private final AiConversationRepository conversationRepo;
    private final AiMessageRepository messageRepo;
    private final boolean enabled;

    public AiConversationForkService(
            AiConversationRepository conversationRepo,
            AiMessageRepository messageRepo,
            @Value("${platform.conversation-fork.enabled:false}") boolean enabled
    ) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public AiConversationEntity forkConversation(UUID conversationId, UUID messageId, UUID userId) {
        if (!enabled) {
            throw new IllegalStateException("conversation fork is disabled");
        }

        AiConversationEntity source = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found"));

        if (source.getDeletedAt() != null || !source.getUserId().equals(userId)) {
            throw new IllegalArgumentException("conversation not available");
        }

        AiMessageEntity anchor = messageRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("message not found"));

        if (!anchor.getConversationId().equals(conversationId)) {
            throw new IllegalArgumentException("message does not belong to conversation");
        }

        List<AiMessageEntity> history = messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<AiMessageEntity> toCopy = new ArrayList<>();
        boolean found = false;
        for (AiMessageEntity msg : history) {
            toCopy.add(msg);
            if (msg.getId().equals(messageId)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("message not found in conversation");
        }

        AiConversationEntity fork = new AiConversationEntity();
        fork.setUserId(userId);
        fork.setTitle(source.getTitle());
        fork.setModel(source.getModel());
        fork.setSystemPrompt(source.getSystemPrompt());
        fork.setSourceConversationId(conversationId);
        fork.setSourceMessageId(messageId);
        AiConversationEntity savedFork = conversationRepo.save(fork);

        // First pass: create copies and assign new UUIDs, build old-to-new ID mapping
        Map<UUID, UUID> idMapping = new HashMap<>(toCopy.size());
        List<AiMessageEntity> copies = new ArrayList<>(toCopy.size());
        for (AiMessageEntity original : toCopy) {
            AiMessageEntity copy = new AiMessageEntity();
            copy.setConversationId(savedFork.getId());
            copy.setRole(original.getRole());
            copy.setContent(original.getContent());
            copy.setContentType(original.getContentType());
            copy.setMediaUrl(original.getMediaUrl());
            copy.setTokenCount(original.getTokenCount());
            copy.setModel(original.getModel());
            copy.setChannelId(original.getChannelId());
            copy.setMultiRoundId(original.getMultiRoundId());
            copy.setBranchIndex(original.getBranchIndex());
            copy.setVersion(original.getVersion());
            copies.add(copy);
        }
        // Save to generate new IDs
        List<AiMessageEntity> savedCopies = messageRepo.saveAll(copies);

        // Build old-to-new ID mapping
        for (int i = 0; i < toCopy.size(); i++) {
            idMapping.put(toCopy.get(i).getId(), savedCopies.get(i).getId());
        }

        // Second pass: remap parentMessageId to point to new forked message IDs
        boolean hasParentLinks = false;
        for (int i = 0; i < toCopy.size(); i++) {
            UUID originalParent = toCopy.get(i).getParentMessageId();
            if (originalParent != null) {
                UUID newParent = idMapping.get(originalParent);
                savedCopies.get(i).setParentMessageId(newParent != null ? newParent : originalParent);
                hasParentLinks = true;
            }
        }
        if (hasParentLinks) {
            messageRepo.saveAll(savedCopies);
        }

        return savedFork;
    }
}
