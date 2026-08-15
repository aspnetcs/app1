package com.webchat.adminapi.market;

import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketAdminControllerTest {

    @Mock
    private MarketAdminService marketAdminService;

    private MarketAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketAdminController(marketAdminService);
    }

    @Test
    void listCatalogRejectsNonAdmin() {
        var response = controller.listCatalog(UUID.randomUUID(), "user", null, null);
        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
    }

    @Test
    void listCatalogReturnsEnvelope() {
        when(marketAdminService.listCatalog("AGENT", "writer")).thenReturn(Map.of(
                "items", List.of(Map.of("assetType", "AGENT", "title", "Writer")),
                "total", 1
        ));

        var response = controller.listCatalog(UUID.randomUUID(), "admin", "AGENT", "writer");
        assertEquals(0, response.code());
        assertEquals(1, response.data().get("total"));
    }

    @Test
    void createCatalogDelegatesToService() {
        when(marketAdminService.createCatalogItem(Map.of("assetType", "MCP", "sourceId", "12"))).thenReturn(Map.of(
                "assetType", "MCP",
                "sourceId", "12"
        ));

        var response = controller.createCatalogItem(UUID.randomUUID(), "admin", Map.of("assetType", "MCP", "sourceId", "12"));
        assertEquals(0, response.code());
        verify(marketAdminService).createCatalogItem(Map.of("assetType", "MCP", "sourceId", "12"));
    }

    @Test
    void listSourceOptionsReturnsServiceData() {
        when(marketAdminService.listSourceOptions("KNOWLEDGE")).thenReturn(List.of(
                Map.of("assetType", "KNOWLEDGE", "sourceId", "kb-1")
        ));

        var response = controller.listSourceOptions(UUID.randomUUID(), "admin", "KNOWLEDGE");
        assertEquals(0, response.code());
        assertEquals(1, response.data().size());
    }
}
