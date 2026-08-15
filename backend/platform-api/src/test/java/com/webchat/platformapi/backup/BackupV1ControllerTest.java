package com.webchat.platformapi.backup;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BackupV1ControllerTest {

    @Mock
    private BackupManifestService backupManifestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BackupV1Controller(backupManifestService)).build();
    }

    @Test
    void manifestRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/backup/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void manifestDelegatesToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(backupManifestService.buildUserManifest(eq(userId), eq(200)))
                .thenReturn(Map.of("schemaVersion", 1, "modules", java.util.List.of("files")));

        mockMvc.perform(
                        get("/api/v1/backup/manifest")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schemaVersion").value(1));

        verify(backupManifestService).buildUserManifest(eq(userId), eq(200));
    }
}

