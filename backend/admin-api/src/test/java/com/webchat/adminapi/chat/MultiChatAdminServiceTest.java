package com.webchat.adminapi.chat;

import com.webchat.platformapi.config.SysConfigService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiChatAdminServiceTest {

    @Test
    void parallelConfigUsesPersistedRuntimeValuesWhenPresent() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.get("platform.multi-chat.enabled")).thenReturn(Optional.of("true"));
        when(sysConfigService.get("platform.multi-chat.max-models")).thenReturn(Optional.of("6"));

        MultiChatAdminService service = new MultiChatAdminService(
                false,
                3,
                false,
                4,
                20,
                sysConfigService
        );

        Map<String, Object> config = service.parallelConfig();

        assertThat(config).containsEntry("enabled", true);
        assertThat(config).containsEntry("maxModels", 6);
    }
}
