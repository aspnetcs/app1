package com.webchat.adminapi.chat;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.config.SysConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MultiChatAdminService {

    private static final String CFG_PARALLEL_ENABLED = "platform.multi-chat.enabled";
    private static final String CFG_PARALLEL_MAX_MODELS = "platform.multi-chat.max-models";
    private static final String CFG_MULTI_AGENT_DISCUSSION_ENABLED = "multi_agent_discussion.enabled";
    private static final String CFG_MULTI_AGENT_DISCUSSION_MAX_AGENTS = "multi_agent_discussion.max_agents";
    private static final String CFG_MULTI_AGENT_DISCUSSION_MAX_ROUNDS = "multi_agent_discussion.max_rounds";
    private static final String CFG_ROLEPLAY_ENABLED = "roleplay.enabled";
    private static final String CFG_ROLEPLAY_MAX_ROLES = "roleplay.max_roles";
    private static final String CFG_ROLEPLAY_MAX_ROUNDS = "roleplay.max_rounds";

    private final boolean parallelEnabled;
    private final int maxModels;
    private final boolean multiAgentDiscussionEnabledDefault;
    private final int multiAgentDiscussionMaxAgentsDefault;
    private final int multiAgentDiscussionMaxRoundsDefault;
    private final SysConfigService sysConfigService;

    public MultiChatAdminService(
            @Value("${platform.multi-chat.enabled:false}") boolean parallelEnabled,
            @Value("${platform.multi-chat.max-models:3}") int maxModels,
            @Value("${platform.multi-chat.multi-agent-discussion.enabled:false}") boolean multiAgentDiscussionEnabledDefault,
            @Value("${platform.multi-chat.multi-agent-discussion.max-agents:4}") int multiAgentDiscussionMaxAgentsDefault,
            @Value("${platform.multi-chat.multi-agent-discussion.max-rounds:20}") int multiAgentDiscussionMaxRoundsDefault,
            SysConfigService sysConfigService
    ) {
        this.parallelEnabled = parallelEnabled;
        this.maxModels = maxModels;
        this.multiAgentDiscussionEnabledDefault = multiAgentDiscussionEnabledDefault;
        this.multiAgentDiscussionMaxAgentsDefault = multiAgentDiscussionMaxAgentsDefault;
        this.multiAgentDiscussionMaxRoundsDefault = multiAgentDiscussionMaxRoundsDefault;
        this.sysConfigService = sysConfigService;
    }

    public Map<String, Object> parallelConfig() {
        return Map.of(
                "enabled", currentParallelEnabled(),
                "maxModels", currentParallelMaxModels(),
                "featureKey", "platform.multi-chat.enabled",
                "notes", List.of(
                        "Single request fans out to multiple model responses.",
                        "Model-level availability is controlled by ai_model_metadata.multi_chat_enabled."
                )
        );
    }

    public Map<String, Object> multiAgentDiscussionConfig() {
        return Map.of(
                "enabled", currentMultiAgentDiscussionEnabled(),
                "maxAgents", currentMultiAgentDiscussionMaxAgents(),
                "maxRounds", currentMultiAgentDiscussionMaxRounds()
        );
    }

    public ApiResponse<Map<String, Object>> updateMultiAgentDiscussionConfig(Map<String, Object> body) {
        if (body.containsKey("enabled")) {
            boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            sysConfigService.set(CFG_MULTI_AGENT_DISCUSSION_ENABLED, String.valueOf(enabled));
        }
        if (body.containsKey("maxAgents")) {
            Integer maxAgents = parseInt(body.get("maxAgents"));
            if (maxAgents == null || maxAgents < 2) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "maxAgents must be >= 2");
            }
            sysConfigService.set(CFG_MULTI_AGENT_DISCUSSION_MAX_AGENTS, String.valueOf(maxAgents));
        }
        if (body.containsKey("maxRounds")) {
            Integer maxRounds = parseInt(body.get("maxRounds"));
            if (maxRounds == null || maxRounds < 1) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "maxRounds must be >= 1");
            }
            sysConfigService.set(CFG_MULTI_AGENT_DISCUSSION_MAX_ROUNDS, String.valueOf(maxRounds));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", currentMultiAgentDiscussionEnabled());
        result.put("maxAgents", currentMultiAgentDiscussionMaxAgents());
        result.put("maxRounds", currentMultiAgentDiscussionMaxRounds());
        return ApiResponse.ok(result);
    }

    private boolean currentParallelEnabled() {
        return sysConfigService.get(CFG_PARALLEL_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(parallelEnabled);
    }

    private int currentParallelMaxModels() {
        return sysConfigService.get(CFG_PARALLEL_MAX_MODELS)
                .map(MultiChatAdminService::parseInt)
                .filter(value -> value != null && value >= 2)
                .orElse(maxModels);
    }

    private boolean currentMultiAgentDiscussionEnabled() {
        return readConfigValue(CFG_MULTI_AGENT_DISCUSSION_ENABLED, CFG_ROLEPLAY_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(multiAgentDiscussionEnabledDefault);
    }

    private int currentMultiAgentDiscussionMaxAgents() {
        return readConfigValue(CFG_MULTI_AGENT_DISCUSSION_MAX_AGENTS, CFG_ROLEPLAY_MAX_ROLES)
                .map(MultiChatAdminService::parseInt)
                .filter(value -> value != null && value >= 2)
                .orElse(multiAgentDiscussionMaxAgentsDefault);
    }

    private int currentMultiAgentDiscussionMaxRounds() {
        return readConfigValue(CFG_MULTI_AGENT_DISCUSSION_MAX_ROUNDS, CFG_ROLEPLAY_MAX_ROUNDS)
                .map(MultiChatAdminService::parseInt)
                .filter(value -> value != null && value >= 1)
                .orElse(multiAgentDiscussionMaxRoundsDefault);
    }

    private Optional<String> readConfigValue(String primaryKey, String legacyKey) {
        Optional<String> primary = sysConfigService.get(primaryKey);
        return primary.isPresent() ? primary : sysConfigService.get(legacyKey);
    }

    static Integer parseInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
