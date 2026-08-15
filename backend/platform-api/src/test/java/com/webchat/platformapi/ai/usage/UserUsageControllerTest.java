package com.webchat.platformapi.ai.usage;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.credits.CreditSettlementSnapshotEntity;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserUsageControllerTest {

    @Test
    void returnsPagedCreditsHistory() {
        AiUsageService usageService = mock(AiUsageService.class);
        CreditsRuntimeService creditsRuntimeService = mock(CreditsRuntimeService.class);
        UUID userId = UUID.randomUUID();

        CreditSettlementSnapshotEntity snapshot = new CreditSettlementSnapshotEntity();
        snapshot.setId(9L);
        snapshot.setRequestId("req-9");
        snapshot.setModelId("model-a");
        snapshot.setModelNameSnapshot("Model A");
        snapshot.setSettlementStatus("settled");
        snapshot.setReservedCredits(12);
        snapshot.setSettledCredits(10);
        snapshot.setRefundedCredits(2);
        snapshot.setCreatedAt(Instant.parse("2026-04-20T00:00:00Z"));

        when(creditsRuntimeService.getHistoryPage(userId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(snapshot), PageRequest.of(0, 20), 1));
        when(creditsRuntimeService.getAccountSummary(userId, null, "user"))
                .thenReturn(Map.of(
                        "hasAccount", true,
                        "effectiveBalance", 88,
                        "creditsSystemEnabled", false,
                        "freeModeEnabled", true
                ));

        UserUsageController controller = new UserUsageController(usageService, creditsRuntimeService);
        ApiResponse<Map<String, Object>> response = controller.creditsHistory(userId, "user", 0, 20);

        assertEquals(ErrorCodes.SUCCESS, response.code());
        assertEquals(false, response.data().get("creditsSystemEnabled"));
        assertEquals(true, response.data().get("freeModeEnabled"));
        assertEquals(1L, response.data().get("total"));
        assertEquals(0, response.data().get("page"));
        assertEquals(20, response.data().get("size"));
        assertInstanceOf(List.class, response.data().get("settlements"));
        List<?> items = (List<?>) response.data().get("settlements");
        assertEquals(1, items.size());
    }

    @Test
    void returnsStableDisabledShapeWhenCreditsRuntimeIsMissing() {
        AiUsageService usageService = mock(AiUsageService.class);
        UUID userId = UUID.randomUUID();

        UserUsageController controller = new UserUsageController(usageService, null);
        ApiResponse<Map<String, Object>> response = controller.creditsHistory(userId, "user", 2, 500);

        assertEquals(ErrorCodes.SUCCESS, response.code());
        assertEquals(false, response.data().get("creditsSystemEnabled"));
        assertEquals(false, response.data().get("freeModeEnabled"));
        assertEquals(0, response.data().get("total"));
        assertEquals(2, response.data().get("page"));
        assertEquals(100, response.data().get("size"));
        assertInstanceOf(Map.class, response.data().get("account"));
        assertInstanceOf(List.class, response.data().get("settlements"));
        assertEquals(0, ((List<?>) response.data().get("settlements")).size());
    }
}
