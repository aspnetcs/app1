package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.extension.UserMcpToolAccessService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatMcpToolContextServiceTest {

    @Test
    void applySavedMcpToolNamesMergesExistingAndSelectedTools() {
        UUID userId = UUID.randomUUID();
        UserMcpToolAccessService accessService = mock(UserMcpToolAccessService.class);
        when(accessService.listSelectedToolNames(userId)).thenReturn(List.of("mcp_tool_a", "mcp_tool_b"));
        ChatMcpToolContextService service = new ChatMcpToolContextService(accessService);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("toolNames", List.of("calculator", "mcp_tool_a"));

        service.applySavedMcpToolNames(userId, request);

        assertEquals(List.of("calculator", "mcp_tool_a", "mcp_tool_b"), request.get("toolNames"));
    }

    @Test
    void applySavedMcpToolNamesLeavesRequestUnchangedWhenNoSelection() {
        UUID userId = UUID.randomUUID();
        UserMcpToolAccessService accessService = mock(UserMcpToolAccessService.class);
        when(accessService.listSelectedToolNames(userId)).thenReturn(List.of());
        ChatMcpToolContextService service = new ChatMcpToolContextService(accessService);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", "gpt-5.4-mini");

        service.applySavedMcpToolNames(userId, request);

        assertEquals(Map.of("model", "gpt-5.4-mini"), request);
    }
}
