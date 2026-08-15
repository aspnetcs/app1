package com.webchat.platformapi.ai.extension;

import com.webchat.platformapi.market.MarketAssetType;
import com.webchat.platformapi.market.UserSavedAssetEntity;
import com.webchat.platformapi.market.UserSavedAssetRepository;
import com.webchat.platformapi.preferences.UserPreferenceEntity;
import com.webchat.platformapi.preferences.UserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserMcpToolAccessServiceTest {

    @Test
    void manualModeReturnsOnlyPreferredServerTools() {
        UUID userId = UUID.randomUUID();
        UserPreferenceRepository preferenceRepository = mock(UserPreferenceRepository.class);
        UserSavedAssetRepository savedAssetRepository = mock(UserSavedAssetRepository.class);
        McpServerRepository mcpServerRepository = mock(McpServerRepository.class);
        McpClientService clientService = mock(McpClientService.class);
        McpProperties properties = new McpProperties();
        properties.setEnabled(true);

        UserPreferenceEntity preference = new UserPreferenceEntity();
        preference.setUserId(userId);
        preference.setMcpMode("manual");
        preference.setPreferredMcpServerId("2");
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(preference));

        UserSavedAssetEntity savedOne = new UserSavedAssetEntity();
        savedOne.setUserId(userId);
        savedOne.setAssetType(MarketAssetType.MCP);
        savedOne.setSourceId("1");
        UserSavedAssetEntity savedTwo = new UserSavedAssetEntity();
        savedTwo.setUserId(userId);
        savedTwo.setAssetType(MarketAssetType.MCP);
        savedTwo.setSourceId("2");
        when(savedAssetRepository.findByUserIdAndAssetTypeOrderBySortOrderAscCreatedAtDesc(userId, MarketAssetType.MCP))
                .thenReturn(List.of(savedOne, savedTwo));

        McpServerEntity serverTwo = new McpServerEntity();
        serverTwo.setId(2L);
        serverTwo.setEnabled(true);
        when(mcpServerRepository.findAllById(any())).thenReturn(List.of(serverTwo));

        when(clientService.getProperties()).thenReturn(properties);
        when(clientService.listAllTools()).thenReturn(List.of(
                new McpClientService.McpTool(1L, "ops", "ticket_lookup", "lookup", java.util.Map.of()),
                new McpClientService.McpTool(2L, "release", "window_lookup", "window", java.util.Map.of())
        ));

        UserMcpToolAccessService service = new UserMcpToolAccessService(
                preferenceRepository,
                savedAssetRepository,
                mcpServerRepository,
                new StaticObjectProvider(clientService)
        );

        assertEquals(1, service.listSelectedTools(userId).size());
        assertEquals("window_lookup", service.listSelectedTools(userId).get(0).name());
        assertEquals(1, service.listSelectedToolNames(userId).size());
    }

    @Test
    void disabledModeReturnsNoTools() {
        UUID userId = UUID.randomUUID();
        UserPreferenceRepository preferenceRepository = mock(UserPreferenceRepository.class);
        UserSavedAssetRepository savedAssetRepository = mock(UserSavedAssetRepository.class);
        McpServerRepository mcpServerRepository = mock(McpServerRepository.class);
        McpClientService clientService = mock(McpClientService.class);
        McpProperties properties = new McpProperties();
        properties.setEnabled(true);

        UserPreferenceEntity preference = new UserPreferenceEntity();
        preference.setUserId(userId);
        preference.setMcpMode("disabled");
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(preference));
        when(clientService.getProperties()).thenReturn(properties);

        UserMcpToolAccessService service = new UserMcpToolAccessService(
                preferenceRepository,
                savedAssetRepository,
                mcpServerRepository,
                new StaticObjectProvider(clientService)
        );

        assertEquals(List.of(), service.listSelectedToolNames(userId));
    }

    private record StaticObjectProvider(McpClientService value) implements ObjectProvider<McpClientService> {
        @Override
        public McpClientService getObject(Object... args) {
            return value;
        }

        @Override
        public McpClientService getIfAvailable() {
            return value;
        }

        @Override
        public McpClientService getIfUnique() {
            return value;
        }

        @Override
        public McpClientService getObject() {
            return value;
        }
    }
}
