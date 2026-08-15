package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record MultiAgentDiscussionNextRequest(
        @JsonAlias("session_id") String sessionId,
        @JsonAlias("agent_index") JsonNode agentIndex,
        List<Map<String, Object>> history,
        @JsonAlias("knowledge_base_ids") List<String> knowledgeBaseIds
) {
}
