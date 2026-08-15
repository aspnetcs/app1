package com.webchat.adminapi.credits;

import com.webchat.platformapi.common.api.ApiResponse;
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
class CreditsAdminControllerTest {

    @Mock
    private CreditsAdminService creditsAdminService;

    private CreditsAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new CreditsAdminController(creditsAdminService);
    }

    @Test
    void getSystemConfigRejectsUnauthenticatedRequests() {
        ApiResponse<?> response = controller.getSystemConfig(null, "admin");

        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(creditsAdminService);
    }

    @Test
    void updateRoleTemplateRejectsNonAdminRole() {
        ApiResponse<?> response = controller.updateRoleTemplate(
                UUID.randomUUID(),
                "user",
                "premium",
                new CreditsAdminController.RoleTemplateUpdateRequest(3000L, "monthly", false)
        );

        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(creditsAdminService);
    }

    @Test
    void adjustUserAccountDelegatesToService() {
        UUID adminUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(creditsAdminService.adjustUserAccount(targetUserId, 500L, "manual grant"))
                .thenReturn(ApiResponse.ok(Map.of("action", Map.of("type", "adjust"))));

        ApiResponse<?> response = controller.adjustUserAccount(
                adminUserId,
                "admin",
                targetUserId,
                new CreditsAdminController.AccountAdjustRequest(500L, "manual grant")
        );

        assertEquals(0, response.code());
        @SuppressWarnings("unchecked")
        Map<String, Object> action = (Map<String, Object>) ((Map<String, Object>) response.data()).get("action");
        assertEquals("adjust", action.get("type"));
    }

    @Test
    void listSettlementsReturnsServicePayload() {
        when(creditsAdminService.listSettlements(0, 20, null, null, null, null, null))
                .thenReturn(ApiResponse.ok(Map.of("total", 2, "items", java.util.List.of())));

        ApiResponse<?> response = controller.listSettlements(
                UUID.randomUUID(),
                "admin",
                0,
                20,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(0, response.code());
        assertEquals(2, ((Map<?, ?>) response.data()).get("total"));
    }
}
