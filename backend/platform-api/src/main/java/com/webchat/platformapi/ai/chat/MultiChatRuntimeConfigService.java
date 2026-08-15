package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MultiChatRuntimeConfigService {

    private static final String KEY_PARALLEL_ENABLED = "platform.multi-chat.enabled";
    private static final String KEY_PARALLEL_MAX_MODELS = "platform.multi-chat.max-models";
    private static final String KEY_DISCUSSION_ENABLED = "multi_agent_discussion.enabled";
    private static final String KEY_DISCUSSION_MAX_AGENTS = "multi_agent_discussion.max_agents";
    private static final String KEY_DISCUSSION_MAX_ROUNDS = "multi_agent_discussion.max_rounds";
    private static final String LEGACY_DISCUSSION_ENABLED = "roleplay.enabled";
    private static final String LEGACY_DISCUSSION_MAX_AGENTS = "roleplay.max_roles";
    private static final String LEGACY_DISCUSSION_MAX_ROUNDS = "roleplay.max_rounds";

    private final SysConfigService sysConfigService;
    private final boolean parallelEnabledDefault;
    private final int parallelMaxModelsDefault;
    private final boolean discussionEnabledDefault;
    private final int discussionMaxAgentsDefault;
    private final int discussionMaxRoundsDefault;

    public MultiChatRuntimeConfigService(
            SysConfigService sysConfigService,
            @Value("${platform.multi-chat.enabled:false}") boolean parallelEnabledDefault,
            @Value("${platform.multi-chat.max-models:3}") int parallelMaxModelsDefault,
            @Value("${platform.multi-chat.multi-agent-discussion.enabled:false}") boolean discussionEnabledDefault,
            @Value("${platform.multi-chat.multi-agent-discussion.max-agents:4}") int discussionMaxAgentsDefault,
            @Value("${platform.multi-chat.multi-agent-discussion.max-rounds:20}") int discussionMaxRoundsDefault
    ) {
        this.sysConfigService = sysConfigService;
        this.parallelEnabledDefault = parallelEnabledDefault;
        this.parallelMaxModelsDefault = Math.max(2, parallelMaxModelsDefault);
        this.discussionEnabledDefault = discussionEnabledDefault;
        this.discussionMaxAgentsDefault = Math.max(2, discussionMaxAgentsDefault);
        this.discussionMaxRoundsDefault = Math.max(1, discussionMaxRoundsDefault);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                readBoolean(KEY_PARALLEL_ENABLED, parallelEnabledDefault),
                readInt(KEY_PARALLEL_MAX_MODELS, parallelMaxModelsDefault, 2, null),
                readBooleanWithFallback(KEY_DISCUSSION_ENABLED, LEGACY_DISCUSSION_ENABLED, discussionEnabledDefault),
                readIntWithFallback(KEY_DISCUSSION_MAX_AGENTS, LEGACY_DISCUSSION_MAX_AGENTS, discussionMaxAgentsDefault, 2, null),
                readIntWithFallback(KEY_DISCUSSION_MAX_ROUNDS, LEGACY_DISCUSSION_MAX_ROUNDS, discussionMaxRoundsDefault, 1, null)
        );
    }

    private boolean readBoolean(String key, boolean fallback) {
        return sysConfigService.get(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    private boolean readBooleanWithFallback(String primaryKey, String legacyKey, boolean fallback) {
        return readConfigValue(primaryKey, legacyKey).map(Boolean::parseBoolean).orElse(fallback);
    }

    private int readInt(String key, int fallback, int min, Integer max) {
        return sysConfigService.get(key)
                .map(MultiChatRuntimeConfigService::parseInt)
                .filter(value -> value != null && value >= min && (max == null || value <= max))
                .orElse(fallback);
    }

    private int readIntWithFallback(String primaryKey, String legacyKey, int fallback, int min, Integer max) {
        return readConfigValue(primaryKey, legacyKey)
                .map(MultiChatRuntimeConfigService::parseInt)
                .filter(value -> value != null && value >= min && (max == null || value <= max))
                .orElse(fallback);
    }

    private Optional<String> readConfigValue(String primaryKey, String legacyKey) {
        Optional<String> primary = sysConfigService.get(primaryKey);
        return primary.isPresent() ? primary : sysConfigService.get(legacyKey);
    }

    private static Integer parseInt(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public record Snapshot(
            boolean parallelEnabled,
            int parallelMaxModels,
            boolean discussionEnabled,
            int discussionMaxAgents,
            int discussionMaxRounds
    ) {
    }
}
