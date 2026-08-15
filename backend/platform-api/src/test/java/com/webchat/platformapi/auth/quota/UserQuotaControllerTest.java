package com.webchat.platformapi.auth.quota;

import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserQuotaControllerTest {

    @Test
    void includesCreditsSummaryForPendingRole() {
        RolePolicyService rolePolicyService = mock(RolePolicyService.class);
        RolePolicyProperties rolePolicyProperties = mock(RolePolicyProperties.class);
        CreditsRuntimeService creditsRuntimeService = mock(CreditsRuntimeService.class);
        RolePolicyProperties.RoleConfig policy = new RolePolicyProperties.RoleConfig();
        UUID userId = UUID.randomUUID();

        when(rolePolicyProperties.getPolicy("user")).thenReturn(policy);
        when(rolePolicyService.getDailyUsageCount(userId)).thenReturn(3L);
        when(rolePolicyService.resolveAllowedModels(userId, "pending")).thenReturn(Set.of("model-a"));
        when(rolePolicyService.resolveRateLimit(userId, "pending")).thenReturn(12);
        when(creditsRuntimeService.getAccountSummary(userId, null, "pending"))
                .thenReturn(Map.of("hasAccount", true, "effectiveBalance", 77));

        UserQuotaController controller = new UserQuotaController(rolePolicyService, rolePolicyProperties, creditsRuntimeService);
        ApiResponse<Map<String, Object>> response = controller.quota(userId, "pending");

        assertEquals(ErrorCodes.SUCCESS, response.code());
        assertEquals("pending", response.data().get("role"));
        Object credits = response.data().get("credits");
        assertInstanceOf(Map.class, credits);
        assertEquals(77, ((Map<?, ?>) credits).get("effectiveBalance"));
    }
}
