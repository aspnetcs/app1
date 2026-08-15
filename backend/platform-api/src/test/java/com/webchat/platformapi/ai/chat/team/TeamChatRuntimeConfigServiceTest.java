package com.webchat.platformapi.ai.chat.team;

import com.webchat.platformapi.config.SysConfigService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamChatRuntimeConfigServiceTest {

    @Test
    void snapshotUsesPersistedTeamOverrides() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.get("platform.team-chat.enabled")).thenReturn(Optional.of("true"));
        when(sysConfigService.get("platform.team-chat.max-models")).thenReturn(Optional.of("7"));
        when(sysConfigService.get("platform.team-chat.max-debate-rounds")).thenReturn(Optional.of("3"));
        when(sysConfigService.get("platform.team-chat.voting-timeout-seconds")).thenReturn(Optional.of("45"));
        when(sysConfigService.get("platform.team-chat.extraction-timeout-seconds")).thenReturn(Optional.of("180"));
        when(sysConfigService.get("platform.team-chat.max-llm-concurrency")).thenReturn(Optional.of("12"));
        when(sysConfigService.get("platform.team-chat.shared-summary-max-chars")).thenReturn(Optional.of("2500"));
        when(sysConfigService.get("platform.team-chat.member-memory-max-chars")).thenReturn(Optional.of("1500"));
        when(sysConfigService.get("platform.team.conversation-ttl-hours")).thenReturn(Optional.of("96"));
        when(sysConfigService.get("platform.team.turn-ttl-minutes")).thenReturn(Optional.of("55"));

        TeamChatRuntimeConfigService service = new TeamChatRuntimeConfigService(
                sysConfigService,
                false,
                5,
                1,
                60,
                120,
                8,
                2000,
                1000,
                72,
                30
        );

        TeamChatRuntimeConfigService.Snapshot snapshot = service.snapshot();
        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.maxModels()).isEqualTo(7);
        assertThat(snapshot.maxDebateRounds()).isEqualTo(3);
        assertThat(snapshot.votingTimeoutSeconds()).isEqualTo(45);
        assertThat(snapshot.extractionTimeoutSeconds()).isEqualTo(180);
        assertThat(snapshot.maxLlmConcurrency()).isEqualTo(12);
        assertThat(snapshot.sharedSummaryMaxChars()).isEqualTo(2500);
        assertThat(snapshot.memberMemoryMaxChars()).isEqualTo(1500);
        assertThat(snapshot.conversationTtlHours()).isEqualTo(96);
        assertThat(snapshot.turnTtlMinutes()).isEqualTo(55);
    }
}
