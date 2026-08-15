package com.webchat.adminapi.codetools;

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
class CodeToolsAdminControllerTest {

    @Mock
    private CodeToolsAdminService service;

    private CodeToolsAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new CodeToolsAdminController(service);
    }

    @Test
    void listRejectsUnauthenticatedRequests() {
        var response = controller.list(null, "admin", 0, 20, null, null);
        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(service);
    }

    @Test
    void listRejectsNonAdminRole() {
        var response = controller.list(UUID.randomUUID(), "user", 0, 20, null, null);
        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(service);
    }

    @Test
    void decideMapsIllegalStateToParamMissing() {
        UUID adminId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var req = new CodeToolsAdminController.DecisionRequest("approve", "ok");
        when(service.decide(adminId, taskId, "approve", "ok"))
                .thenThrow(new IllegalStateException("task is not pending"));

        var response = controller.decide(adminId, "admin", taskId, req);

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("task is not pending", response.message());
    }

    @Test
    void decideReturnsOkPayloadWhenServiceSucceeds() {
        UUID adminId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var req = new CodeToolsAdminController.DecisionRequest("approve", "ok");
        when(service.decide(adminId, taskId, "approve", "ok"))
                .thenReturn(Map.of("id", taskId.toString(), "status", "approved"));

        var response = controller.decide(adminId, "admin", taskId, req);

        assertEquals(0, response.code());
        assertEquals("approved", response.data().get("status"));
    }
}

