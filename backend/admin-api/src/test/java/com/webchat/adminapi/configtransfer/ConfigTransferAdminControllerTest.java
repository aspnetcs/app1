package com.webchat.adminapi.configtransfer;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigTransferAdminControllerTest {

    @Mock
    private ConfigTransferService configTransferService;

    private ConfigTransferAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigTransferAdminController(configTransferService);
    }

    @Test
    void exportSplitsModulesAndDelegatesToService() {
        ConfigTransferPayload payload = new ConfigTransferPayload(
                1,
                "2026-03-23T00:00:00Z",
                false,
                List.of("channels", "templates"),
                Map.of(),
                Map.of()
        );
        when(configTransferService.exportPayload(List.of("channels", "templates"), false)).thenReturn(payload);

        var response = controller.export(
                UUID.randomUUID(),
                "admin",
                "channels, templates",
                false
        );

        assertEquals(0, response.code());
        assertEquals(payload, response.data());
        verify(configTransferService).exportPayload(eq(List.of("channels", "templates")), eq(false));
    }

    @Test
    void previewReturnsParamMissingWhenServiceRejectsPayload() {
        ConfigTransferPayload payload = new ConfigTransferPayload(1, null, false, List.of("channels"), Map.of(), Map.of());
        when(configTransferService.preview(payload))
                .thenThrow(new ConfigTransferService.ConfigTransferException("invalid payload"));

        var response = controller.preview(UUID.randomUUID(), "admin", payload);

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("invalid payload", response.message());
    }

    @Test
    void inspectDelegatesToService() {
        ConfigTransferPayload payload = new ConfigTransferPayload(2, "2026-03-23T00:00:00Z", false, List.of("channels"), Map.of(), Map.of());
        when(configTransferService.inspect(payload)).thenReturn(Map.of(
                "schemaVersion", 2,
                "expectedSchemaVersion", 1,
                "compatible", false
        ));

        var response = controller.inspect(UUID.randomUUID(), "admin", payload);

        assertEquals(0, response.code());
        assertEquals(false, ((Map<?, ?>) response.data()).get("compatible"));
        verify(configTransferService).inspect(eq(payload));
    }

    @Test
    void importRejectsMissingAdminAuthentication() {
        ConfigTransferPayload payload = new ConfigTransferPayload(1, null, false, List.of(), Map.of(), Map.of());

        var response = controller.importConfig(null, "admin", payload);

        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        assertNull(response.data());
        verifyNoInteractions(configTransferService);
    }
}
