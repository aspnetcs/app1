package com.webchat.platformapi.memory;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemoryEntryControllerTest {

    @Mock
    private MemoryEntryService memoryEntryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MemoryEntryController(memoryEntryService)).build();
    }

    @Test
    void listRejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/v1/memory/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void listReturnsEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        when(memoryEntryService.listEntries(eq(userId), eq(5))).thenReturn(List.of(
                Map.of("id", "1", "summary", "remember this")
        ));

        mockMvc.perform(get("/api/v1/memory/entries")
                        .param("limit", "5")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].summary").value("remember this"));
    }

    @Test
    void deleteMapsMissingEntryToParamError() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("memory entry not found"))
                .when(memoryEntryService)
                .deleteEntry(userId, entryId);

        mockMvc.perform(delete("/api/v1/memory/entries/{entryId}", entryId)
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("memory entry not found"));
    }
}
