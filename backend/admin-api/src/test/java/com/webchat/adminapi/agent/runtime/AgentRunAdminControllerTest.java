package com.webchat.adminapi.agent.runtime;

import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunAdminControllerTest {

    @Mock
    private AgentRunAdminService runAdminService;

    private AgentRunAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentRunAdminController(runAdminService);
    }

    @Test
    void listRejectsUnauthenticatedRequests() {
        var response = controller.list(null, "admin", 0, 20, null, null);
        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(runAdminService);
    }

    @Test
    void listRejectsNonAdminRole() {
        var response = controller.list(UUID.randomUUID(), "user", 0, 20, null, null);
        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(runAdminService);
    }

    @Test
    void getMapsIllegalArgumentExceptionToParamMissing() {
        UUID runId = UUID.randomUUID();
        when(runAdminService.getRun(runId)).thenThrow(new IllegalArgumentException("run not found"));

        var response = controller.get(UUID.randomUUID(), "admin", runId);

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("run not found", response.message());
    }

    @Test
    void decideMapsIllegalStateToParamMissing() {
        UUID adminId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        var req = new AgentRunAdminController.DecisionRequest("approve", null, "ok");
        when(runAdminService.decide(adminId, runId, "approve", null, "ok"))
                .thenThrow(new IllegalStateException("approval is not pending"));

        var response = controller.decide(adminId, "admin", runId, req);

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("approval is not pending", response.message());
    }

    @Test
    void decideReturnsOkPayloadWhenServiceSucceeds() {
        UUID adminId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        var req = new AgentRunAdminController.DecisionRequest("approve", 1L, "ok");
        when(runAdminService.decide(adminId, runId, "approve", 1L, "ok"))
                .thenReturn(Map.of("id", runId.toString(), "status", "approved"));

        var response = controller.decide(adminId, "admin", runId, req);

        assertEquals(0, response.code());
        assertEquals("approved", response.data().get("status"));
    }
}

