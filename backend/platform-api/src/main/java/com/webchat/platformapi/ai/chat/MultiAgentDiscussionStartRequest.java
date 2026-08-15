package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record MultiAgentDiscussionStartRequest(
        @JsonAlias("agent_ids") List<String> agentIds,
        String topic,
        @JsonAlias("knowledge_base_ids") List<String> knowledgeBaseIds
) {
}
