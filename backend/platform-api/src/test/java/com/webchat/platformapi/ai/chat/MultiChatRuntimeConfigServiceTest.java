package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.config.SysConfigService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiChatRuntimeConfigServiceTest {

    @Test
    void snapshotUsesSysConfigOverridesAndLegacyDiscussionFallback() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.get("platform.multi-chat.enabled")).thenReturn(Optional.of("true"));
        when(sysConfigService.get("platform.multi-chat.max-models")).thenReturn(Optional.of("6"));
        when(sysConfigService.get("multi_agent_discussion.enabled")).thenReturn(Optional.empty());
        when(sysConfigService.get("multi_agent_discussion.max_agents")).thenReturn(Optional.empty());
        when(sysConfigService.get("multi_agent_discussion.max_rounds")).thenReturn(Optional.empty());
        when(sysConfigService.get("roleplay.enabled")).thenReturn(Optional.of("true"));
        when(sysConfigService.get("roleplay.max_roles")).thenReturn(Optional.of("5"));
        when(sysConfigService.get("roleplay.max_rounds")).thenReturn(Optional.of("18"));

        MultiChatRuntimeConfigService service = new MultiChatRuntimeConfigService(
                sysConfigService,
                false,
                3,
                false,
                4,
                20
        );

        MultiChatRuntimeConfigService.Snapshot snapshot = service.snapshot();
        assertThat(snapshot.parallelEnabled()).isTrue();
        assertThat(snapshot.parallelMaxModels()).isEqualTo(6);
        assertThat(snapshot.discussionEnabled()).isTrue();
        assertThat(snapshot.discussionMaxAgents()).isEqualTo(5);
        assertThat(snapshot.discussionMaxRounds()).isEqualTo(18);
    }
}
