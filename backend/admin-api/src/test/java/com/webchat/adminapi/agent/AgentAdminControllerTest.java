package com.webchat.adminapi.agent;

import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAdminControllerTest {

    @Mock
    private AgentService agentService;

    private AgentAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentAdminController(agentService);
    }

    @Test
    void listWrapsPagedResultForAdminClients() {
        var page = new PageImpl<>(
                List.of(Map.<String, Object>of("name", "Prompt Coach", "category", "prompt")),
                PageRequest.of(1, 10),
                11
        );
        when(agentService.listForAdmin("catalog", "prompt", true, "USER", false, 1, 10)).thenReturn(page);

        var response = controller.list(UUID.randomUUID(), "admin", 1, 10, "catalog", "prompt", true, "USER", false);

        assertEquals(0, response.code());
        assertEquals(11L, response.data().get("total"));
        assertEquals(1, response.data().get("page"));
        assertEquals(10, response.data().get("size"));
        verify(agentService).listForAdmin(eq("catalog"), eq("prompt"), eq(true), eq("USER"), eq(false), eq(1), eq(10));
    }

    @Test
    void createMapsIllegalArgumentExceptionToParamMissing() {
        Map<String, Object> body = Map.of("scope", "USER", "name", "Prompt Coach");
        when(agentService.createForAdmin(body)).thenThrow(new IllegalArgumentException("userId required for USER scope"));

        var response = controller.create(UUID.randomUUID(), "admin", body);

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("userId required for USER scope", response.message());
        assertNull(response.data());
    }

    @Test
    void updateMapsIllegalArgumentExceptionToParamMissing() {
        UUID agentId = UUID.randomUUID();
        Map<String, Object> body = Map.of("scope", "USER", "name", "Prompt Coach");
        when(agentService.updateForAdmin(agentId, body)).thenThrow(new IllegalArgumentException("userId required for USER scope"));

        var response = controller.update(UUID.randomUUID(), "admin", agentId, body);

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("userId required for USER scope", response.message());
        assertNull(response.data());
    }

    @Test
    void createRejectsNonAdminRole() {
        Map<String, Object> body = Map.of("name", "Prompt Coach");

        var response = controller.create(UUID.randomUUID(), "user", body);

        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(agentService);
    }
}
