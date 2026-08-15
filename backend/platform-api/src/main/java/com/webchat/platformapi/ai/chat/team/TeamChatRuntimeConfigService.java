package com.webchat.platformapi.ai.chat.team;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TeamChatRuntimeConfigService {

    private static final String KEY_ENABLED = "platform.team-chat.enabled";
    private static final String KEY_MAX_MODELS = "platform.team-chat.max-models";
    private static final String KEY_MAX_DEBATE_ROUNDS = "platform.team-chat.max-debate-rounds";
    private static final String KEY_VOTING_TIMEOUT_SECONDS = "platform.team-chat.voting-timeout-seconds";
    private static final String KEY_EXTRACTION_TIMEOUT_SECONDS = "platform.team-chat.extraction-timeout-seconds";
    private static final String KEY_MAX_LLM_CONCURRENCY = "platform.team-chat.max-llm-concurrency";
    private static final String KEY_SHARED_SUMMARY_MAX_CHARS = "platform.team-chat.shared-summary-max-chars";
    private static final String KEY_MEMBER_MEMORY_MAX_CHARS = "platform.team-chat.member-memory-max-chars";
    private static final String KEY_CONVERSATION_TTL_HOURS = "platform.team.conversation-ttl-hours";
    private static final String KEY_TURN_TTL_MINUTES = "platform.team.turn-ttl-minutes";

    private final SysConfigService sysConfigService;
    private final Snapshot defaults;

    public TeamChatRuntimeConfigService(
            SysConfigService sysConfigService,
            @Value("${platform.team-chat.enabled:false}") boolean enabledDefault,
            @Value("${platform.team-chat.max-models:5}") int maxModelsDefault,
            @Value("${platform.team-chat.max-debate-rounds:1}") int maxDebateRoundsDefault,
            @Value("${platform.team-chat.voting-timeout-seconds:60}") int votingTimeoutSecondsDefault,
            @Value("${platform.team-chat.extraction-timeout-seconds:120}") int extractionTimeoutSecondsDefault,
            @Value("${platform.team-chat.max-llm-concurrency:8}") int maxLlmConcurrencyDefault,
            @Value("${platform.team-chat.shared-summary-max-chars:2000}") int sharedSummaryMaxCharsDefault,
            @Value("${platform.team-chat.member-memory-max-chars:1000}") int memberMemoryMaxCharsDefault,
            @Value("${platform.team.conversation-ttl-hours:72}") int conversationTtlHoursDefault,
            @Value("${platform.team.turn-ttl-minutes:30}") int turnTtlMinutesDefault
    ) {
        this.sysConfigService = sysConfigService;
        this.defaults = new Snapshot(
                enabledDefault,
                Math.max(2, maxModelsDefault),
                Math.max(1, maxDebateRoundsDefault),
                Math.max(10, votingTimeoutSecondsDefault),
                Math.max(30, extractionTimeoutSecondsDefault),
                Math.max(2, maxLlmConcurrencyDefault),
                Math.max(200, sharedSummaryMaxCharsDefault),
                Math.max(200, memberMemoryMaxCharsDefault),
                Math.max(1, conversationTtlHoursDefault),
                Math.max(5, turnTtlMinutesDefault)
        );
    }

    public Snapshot snapshot() {
        return new Snapshot(
                readBoolean(KEY_ENABLED, defaults.enabled()),
                readInt(KEY_MAX_MODELS, defaults.maxModels(), 2, 10),
                readInt(KEY_MAX_DEBATE_ROUNDS, defaults.maxDebateRounds(), 1, 5),
                readInt(KEY_VOTING_TIMEOUT_SECONDS, defaults.votingTimeoutSeconds(), 10, 300),
                readInt(KEY_EXTRACTION_TIMEOUT_SECONDS, defaults.extractionTimeoutSeconds(), 30, 600),
                readInt(KEY_MAX_LLM_CONCURRENCY, defaults.maxLlmConcurrency(), 2, 20),
                readInt(KEY_SHARED_SUMMARY_MAX_CHARS, defaults.sharedSummaryMaxChars(), 200, 10000),
                readInt(KEY_MEMBER_MEMORY_MAX_CHARS, defaults.memberMemoryMaxChars(), 200, 5000),
                readInt(KEY_CONVERSATION_TTL_HOURS, defaults.conversationTtlHours(), 1, 720),
                readInt(KEY_TURN_TTL_MINUTES, defaults.turnTtlMinutes(), 5, 1440)
        );
    }

    private boolean readBoolean(String key, boolean fallback) {
        return sysConfigService.get(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    private int readInt(String key, int fallback, int min, int max) {
        return sysConfigService.get(key)
                .map(TeamChatRuntimeConfigService::parseInt)
                .filter(value -> value != null && value >= min && value <= max)
                .orElse(fallback);
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
            boolean enabled,
            int maxModels,
            int maxDebateRounds,
            int votingTimeoutSeconds,
            int extractionTimeoutSeconds,
            int maxLlmConcurrency,
            int sharedSummaryMaxChars,
            int memberMemoryMaxChars,
            int conversationTtlHours,
            int turnTtlMinutes
    ) {
    }
}
